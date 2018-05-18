package de.fzi.osh.device.battery;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersResponse;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.msg.WriteCoilRequest;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;

import de.fzi.osh.core.data.Json;
import de.fzi.osh.device.Device;
import de.fzi.osh.device.battery.configuration.BatteryConfiguration;
import de.fzi.osh.device.battery.data.BatteryStateData;
import de.fzi.osh.device.battery.data.BatteryStateData.BatteryState;
import de.fzi.osh.device.battery.data.BatteryStateData.GridState;
import de.fzi.osh.device.battery.data.BatteryStateData.PhaseMode;
import de.fzi.osh.device.battery.data.BatteryStateData.SystemState;
import de.fzi.osh.device.time.Time;
import de.fzi.osh.time.TimeService;
import de.fzi.osh.time.realtime.RealTimeService;
import de.fzi.osh.wamp.device.DriverState;
import de.fzi.osh.wamp.rems.ControlRequest;

public class Battery extends Device<Battery, BatteryConfiguration>  {

	private static Logger log = Logger.getLogger(Battery.class.getName());
	
	private BatteryCommunication bus;
	
	// scheduling
	private BatteryScheduler scheduler;
	
	// modbus
	private TCPMasterConnection modbusConnection;
	
	private BatteryStateData currentState;
	
	// rems
	private ControlRequest remsRequest;
	
	// whether the driver is intervening to prevent device damage
	private boolean intervention = false;
	
	// writing to modbus
	private int communicationCycle = 0; // counter for communication
	private boolean writeToModbus = false;
	private long realPowerReq; // ( "+" = discharge; "-" charge ) [W]
	
	// enabling and disabling driver
	private DriverState driverState;

	// for preventing frequent reading operations
	private Instant previousPolling = Time.service().nowAsInstant();
		
	
	public Battery(BatteryConfiguration configuration) {
		super(configuration);
				
		// initialize OC
		controller = new BatteryController(this);
		observer = new BatteryObserver(this, controller);
		
		// setup bus connection
        bus = new BatteryCommunication(this);
        bus.open();
		
		try {
			modbusConnection = new TCPMasterConnection(InetAddress.getByName(configuration.batteryAddress));
			modbusConnection.setPort(configuration.batteryPort);	
			driverState = DriverState.Standby;
		} catch (UnknownHostException e) {
			log.severe("Battery ip could not be determined using '" + configuration.batteryAddress + "'");
			return;
		}
		

		// create scheduler an measure every $configuration.samplingInterval ms
		Time.service().scheduleAtRate(this, 0, configuration.communicationInterval);
		
		// create battery scheduler
		scheduler = new BatteryScheduler(this);
		Time.service().scheduleAtRate(scheduler, configuration.schedulerBackgroundTaskPeriod, configuration.schedulerBackgroundTaskPeriod);
	}

	@Override
	public void run() {
		// the actual driver		
		try {
			
			// prevent frequent reading operations (when executions pile up due to connection issues)
			Instant now = Time.service().nowAsInstant();
			long deltaTime = Duration.between(previousPolling, now).toMillis(); // time since last polling in ms
			if(deltaTime <= configuration.communicationInterval / 2) {
				// execution has piled up
				return;
			}
			previousPolling = now;
			
			// establish a connection
			if(!modbusConnection.isConnected()) {
				try {
					modbusConnection.connect();
				} catch (Exception e) {
					log.severe("Could not establish modbus connection.");
					log.severe(e.toString());
					return;
				}
			}
			currentState = pollBatteryData();
			if(currentState == null) {
				modbusConnection.close();
				communicationCycle = 0;
			}			
			// is the driver allowed to operate?
			else if(driverState == DriverState.On) {				
								
				// notify observer of updates
				// -> controller could still change the realPowerReq value
				observer.update(currentState);	
				
				// battery state management
				if(currentState.systemState != SystemState.Auto && currentState.systemState != SystemState.LineCommutated) {
					log.finest("System state is '" + currentState.systemState + "'. Requesting '" + SystemState.LineCommutated.toString() + "'." );
					// State is not auto or line commutated => request auto state
					ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
					// create request
					SimpleRegister register = new SimpleRegister(SystemState.LineCommutated.getValue());
					WriteSingleRegisterRequest writeSingleRegisterRequest = new WriteSingleRegisterRequest(243, register); // register 244
					// do request
					transaction.setRequest(writeSingleRegisterRequest);
					transaction.execute();
				}

				
				// [device safety]
				if(currentState.stateOfCharge < configuration.minStateOfCharge) {
					int power = Math.min(configuration.maxFlexibilityCharge, configuration.socInterventionPower);
					scheduler.schedulePower(Time.service().now(), power);
					setRealPower(-power);
					
					log.warning("Passed lower SOC bound. Charging with " + power + "W.");
					intervention = true;
				} else if(currentState.stateOfCharge > configuration.maxStateOfCharge) {
					int power = -Math.min(configuration.maxFlexibilityDischarge, configuration.socInterventionPower);
					scheduler.schedulePower(Time.service().now(), power);
					setRealPower(-power);
					
					log.warning("Passed upper SOC bound. Discharging with " + (-power) + "W.");
					intervention = true;
				} else {
					if(intervention == true) {
						intervention = false;
						scheduler.schedulePower(Time.service().now(), 0);
					}
				}
				
				// write updates if necessary				
				if(currentState.systemState != SystemState.Off && writeToModbus == true) {
					
					// create transaction
					ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
					// create request
					SimpleRegister register = new SimpleRegister(0);
					
					if(configuration.threePhasePowerControl == false) {
						System.out.println("Register[31] = " + realPowerReq / 100);
						// single phase mode
						register.setValue((short)(realPowerReq / 100));
						WriteSingleRegisterRequest writeSingleRegisterRequest = new WriteSingleRegisterRequest(31, register); // register 32
						// do request
						transaction.setRequest(writeSingleRegisterRequest);
						transaction.execute();
					} else {
						// three phase mode
						// distribute request evenly on all phases
						int phasePowerRequest = (int)(realPowerReq / 3);
						short highWord = intToShortH(phasePowerRequest);
						short lowWord = intToShortL(phasePowerRequest);

						// register 36
						System.out.println("Register[35] = " + highWord);
						register.setValue(highWord);
						WriteSingleRegisterRequest writeSingleRegisterRequest = new WriteSingleRegisterRequest(35, register);
						transaction.setRequest(writeSingleRegisterRequest);
						transaction.execute();
						// register 37
						System.out.println("Register[36] = " + lowWord);
						register.setValue(lowWord);
						writeSingleRegisterRequest = new WriteSingleRegisterRequest(36, register);
						transaction.setRequest(writeSingleRegisterRequest);
						transaction.execute();
						// register 38
						System.out.println("Register[37] = " + highWord);
						register.setValue(highWord);
						writeSingleRegisterRequest = new WriteSingleRegisterRequest(37, register);
						transaction.setRequest(writeSingleRegisterRequest);
						transaction.execute();
						// register 39
						System.out.println("Register[38] = " + lowWord);
						register.setValue(lowWord);
						writeSingleRegisterRequest = new WriteSingleRegisterRequest(38, register);
						transaction.setRequest(writeSingleRegisterRequest);
						transaction.execute();
						
						// recalculate for an exact match
						phasePowerRequest = (int)(realPowerReq - 2 * phasePowerRequest);
						highWord = intToShortH(phasePowerRequest);
						lowWord = intToShortL(phasePowerRequest);
						// register 40
						System.out.println("Register[39] = " + highWord);
						register.setValue(highWord);
						writeSingleRegisterRequest = new WriteSingleRegisterRequest(39, register);
						transaction.setRequest(writeSingleRegisterRequest);
						transaction.execute();
						// register 41
						register.setValue(lowWord);
						System.out.println("Register[40] = " + lowWord);
						writeSingleRegisterRequest = new WriteSingleRegisterRequest(40, register);
						transaction.setRequest(writeSingleRegisterRequest);
						transaction.execute();
					}									
					writeToModbus = false;
				}
				
				log.finest(currentState.toString());
			} 
			// rems control
			else if(driverState == DriverState.Standby) {
				if(remsRequest != null){
					// create transaction
					ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
					
					if(remsRequest.coils != null) {
						// write coils
						for(Map.Entry<Short, Boolean> entry : remsRequest.coils.entrySet()) {
							WriteCoilRequest writeCoilRequest = new WriteCoilRequest(entry.getKey(), entry.getValue());
							transaction.setRequest(writeCoilRequest);
							transaction.execute();
						}
					}
					
					if(remsRequest.registers != null) {
						// write registers
						for(Map.Entry<Short, Short> entry : remsRequest.registers.entrySet()) {
							SimpleRegister register = new SimpleRegister(entry.getValue());
							WriteSingleRegisterRequest writeRegisterRequest = new WriteSingleRegisterRequest(entry.getKey(), register);
							transaction.setRequest(writeRegisterRequest);
							transaction.execute();
						}
					}
					
					remsRequest = null;
				}
			}
			
			// close connection
			communicationCycle++;
			if(communicationCycle > configuration.reconnectionInterval) {
				modbusConnection.close();
				System.out.println("reconnect");
				communicationCycle = 0;
			}
		} catch(Exception e) {
			log.severe("An exception has been caught! " + e.toString());
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Helper for converting register data to integer values.
	 * 
	 * @param value0 high word
	 * @param value1 low word
	 * @return
	 */
	private static int shortToInt(int value0, int value1) {
		return (((int)value0) << 16) | value1;  
	}
	
	/**
	 * Helper function for int to register (=short) conversion for register address + 0 [high word]
	 * 
	 * @param value
	 * @return
	 */
	private static short intToShortH(int value) {
		return (short)((value & 0xFFFF0000) >> 16);
	}

	/**
	 * Helper function for int to register (=short) conversion for register address + 1 [low word]
	 * 
	 * @param value
	 * @return
	 */
	private static short intToShortL(int value) {
		return (short)(value & 0x0000FFFF);
	}
	
	/**
	 * Polls all relevant modbus registers
	 * 
	 * @return
	 */
	private BatteryStateData pollBatteryData() {
		BatteryStateData newState = new BatteryStateData();
		
		// create transaction
		ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
		
		//
		// [!] keep in mind: response uses relative reference numbers [!]
		//
		
		// read register ranges in blocks (like advised in documentation)
		
		ReadInputRegistersRequest readInputRegistersRequest;
		ReadInputRegistersResponse readInputRegistersResponse;
		// read (input) registers 1 - 15 		
		try {
			readInputRegistersRequest = new ReadInputRegistersRequest(0, 15);
			// do request
			transaction.setRequest(readInputRegistersRequest);
			
			transaction.execute();
			
			// get response
			readInputRegistersResponse = (ReadInputRegistersResponse) transaction.getResponse();

			newState.gridState = GridState.fromValue(readInputRegistersResponse.getRegisterValue(0)); 
			newState.realPower = (short)readInputRegistersResponse.getRegisterValue(1);
			newState.reactivePower = (short)readInputRegistersResponse.getRegisterValue(2);
			newState.cosPhi = readInputRegistersResponse.getRegisterValue(3);
			newState.phaseMode = PhaseMode.fromValue(readInputRegistersResponse.getRegisterValue(4));	
			newState.maxRealPowerCharge = readInputRegistersResponse.getRegisterValue(13);
			newState.maxRealPowerDischarge = readInputRegistersResponse.getRegisterValue(14);
		} catch (Exception e) {
			log.severe("Modbus request failed on registers 1 - 15.");
			e.printStackTrace();
			log.severe(e.toString());
			return null;
		}	
		
		// read (input) register 126 - 146 (incl.)
		try {				
			readInputRegistersRequest = new ReadInputRegistersRequest(125, 147 - 125);
			// do request
			transaction.setRequest(readInputRegistersRequest);
			
			transaction.execute();
			
			// get response
			readInputRegistersResponse = (ReadInputRegistersResponse) transaction.getResponse();
			newState.stateOfCharge = readInputRegistersResponse.getRegisterValue(0); // Register 126
			newState.stateOfHealth = readInputRegistersResponse.getRegisterValue(1); // Register 127
			newState.batteryState = BatteryState.fromValue(readInputRegistersResponse.getRegisterValue(141 - 125)); // Register 142
			
			newState.energyUntilEmpty = shortToInt(
					readInputRegistersResponse.getRegisterValue(143 - 125), // Register 144
					readInputRegistersResponse.getRegisterValue(144 - 125)); // Register 145
			
			newState.energyUntilFull = shortToInt(
					readInputRegistersResponse.getRegisterValue(145 - 125), // Register 146
					readInputRegistersResponse.getRegisterValue(146 - 125)); // Register 147
		} catch (Exception e) {
			log.severe("Modbus request failed on registers 126 - 146.");
			log.severe(e.toString());
			return null;
		}
		
		
		// read (input) register 201 - 239
		try {
			readInputRegistersRequest = new ReadInputRegistersRequest(200, 39);
			// do request
			transaction.setRequest(readInputRegistersRequest);
			
			transaction.execute();
			
			// get response
			readInputRegistersResponse = (ReadInputRegistersResponse) transaction.getResponse();		
			newState.systemState = SystemState.fromValue(readInputRegistersResponse.getRegisterValue(0)); // Register 201
			
			newState.systemErrorCode[0] = (short) readInputRegistersResponse.getRegisterValue(35);
			newState.systemErrorCode[1] = (short) readInputRegistersResponse.getRegisterValue(36);
			newState.systemErrorCode[2] = (short) readInputRegistersResponse.getRegisterValue(37);
			newState.systemErrorCode[3] = (short) readInputRegistersResponse.getRegisterValue(38);
		} catch (Exception e) {
			log.severe("Modbus request failed on register 201.");
			log.severe(e.toString());
			return null;
		}
		
		ReadMultipleRegistersRequest readMultipleRegistersRequest;
		ReadMultipleRegistersResponse readMultipleRegistersResponse;
		// read power req. 
		try {
			if(configuration.threePhasePowerControl == false) {
				// read register 32
				readMultipleRegistersRequest = new ReadMultipleRegistersRequest(31, 1);
				// do request
				transaction.setRequest(readMultipleRegistersRequest);				
				transaction.execute();
				// get response
				readMultipleRegistersResponse = (ReadMultipleRegistersResponse) transaction.getResponse();
				newState.realPowerReq = (short)readMultipleRegistersResponse.getRegisterValue(0);
			} else {
				// three phase mode
				// read registers 36 - 41
				readMultipleRegistersRequest = new ReadMultipleRegistersRequest(35, 6);
				// do request
				transaction.setRequest(readMultipleRegistersRequest);				
				transaction.execute();
				// get response
				readMultipleRegistersResponse = (ReadMultipleRegistersResponse) transaction.getResponse();
				newState.powerL1Req = shortToInt(	(short)readMultipleRegistersResponse.getRegisterValue(0),
													(short)readMultipleRegistersResponse.getRegisterValue(1));
				newState.powerL2Req = shortToInt(	(short)readMultipleRegistersResponse.getRegisterValue(2),
													(short)readMultipleRegistersResponse.getRegisterValue(3));
				newState.powerL3Req = shortToInt(	(short)readMultipleRegistersResponse.getRegisterValue(4),
													(short)readMultipleRegistersResponse.getRegisterValue(5));
			}
		} catch (Exception e) {
			log.severe("Modbus request failed on " + (configuration.threePhasePowerControl ? "registers 36-41." : "register 32." ));
			log.severe(e.toString());
			return null;
		}		
		
		// read register 244
		try {
			readMultipleRegistersRequest = new ReadMultipleRegistersRequest(243, 1);
			// do request
			transaction.setRequest(readMultipleRegistersRequest);
			
			transaction.execute();

			// get response
			readMultipleRegistersResponse = (ReadMultipleRegistersResponse) transaction.getResponse();
			newState.systemStateRequest = SystemState.fromValue(readMultipleRegistersResponse.getRegisterValue(0));
		} catch (Exception e) {
			log.severe("Modbus request failed on register 244.");
			log.severe(e.toString());
			return null;
		}		
		
		return newState;
	}
	
	/**
	 * Sets the driver state
	 * 
	 * @param state
	 */
	public boolean setDriverState(DriverState state) {
		
		// nothing to do when state matches
		if(driverState != state) {
			// update state and act accordingly
			driverState = state;
			
			if(state == DriverState.On) {
				log.info("State: On");
				
				// forget REMS commands
				remsRequest = null;
				writeToModbus = true;

				// establish a connection
				if(!modbusConnection.isConnected()) {
					try {
						modbusConnection.connect();
						
						// retrieve latest battery data
						currentState = pollBatteryData();
						
						//modbusConnection.close();
						communicationCycle = 0;
					} catch (Exception e) {
						log.severe("Could not establish modbus connection.");
						log.severe(e.toString());
					}
				}
					
				// announce changes
				try {
					publishScheduleChanged(Time.service().now());
				} catch (Exception e) {
					log.severe("Could not emit changed signal");
				}
			} else {
				log.info("State: Standby");
				
				// close connection
				/*if(modbusConnection.isConnected()) {
					modbusConnection.close();
				}*/
				
				/*
				// delete schedule
				for(Task task : scheduler.getScheduledFlexibilities()) {
					scheduler.unscheduleFlexibility(task.id);
				}
					
				// announce changes
				try {
					publishScheduleChanged(Instant.now().getEpochSecond());
				} catch (Exception e) {
					log.severe("Could not emit changed signal");
				}*/
			}
		}				
		return true;
	}
	
	/**
	 * Returns the driver state
	 * 
	 * @return
	 */
	public DriverState getDriverState() {
		return driverState;
	}
	
	public void setRemsRequest(ControlRequest request) {
		if(driverState != DriverState.Standby) {
			log.warning("Received Rems commands while being enabled. Command will be ignored.");
		} else {
			System.out.println(" - REMS request - ");
			
			if(configuration.threePhasePowerControl && request.registers != null) {
				System.out.println("Translating to 3 phase mode.");
				
				// create a new map and replace old one
				Map<Short, Short> translatedRegisters = new HashMap<Short,Short>();
				
				// Note: containsKey doesn't work since map uses Short instead of short !!!
				Iterator<Map.Entry<Short, Short>> iterator = request.registers.entrySet().iterator();
				while(iterator.hasNext()) {
					Map.Entry<Short, Short> entry = iterator.next();
					
					if(entry.getKey() == 31) {
						// map from single phase to three phase control
						int requestedPower = entry.getValue();
						System.out.println("Register[31] = " + requestedPower + " => Registers[35-40]");
						requestedPower *= 100;
						
						// three phase mode
						// distribute request evenly on all phases
						int phasePowerRequest = (int)(requestedPower / 3);
						short highWord = intToShortH(phasePowerRequest);
						short lowWord = intToShortL(phasePowerRequest);
		
						// register 36
						translatedRegisters.put((short)35, highWord);
						// register 37
						translatedRegisters.put((short)36, lowWord);
						// register 38
						translatedRegisters.put((short)37, highWord);
						// register 39
						translatedRegisters.put((short)38, lowWord);
						
						// recalculate for an exact match
						phasePowerRequest = (int)(requestedPower - 2 * phasePowerRequest);
						highWord = intToShortH(phasePowerRequest);
						lowWord = intToShortL(phasePowerRequest);
						// register 40
						translatedRegisters.put((short)39, highWord);
						// register 41
						translatedRegisters.put((short)40, lowWord);
					} else if(entry.getKey() == 32) {
						// map from single phase to three phase control
						int requestedPower = entry.getValue();
						System.out.println("Register[32] = " + requestedPower + " => Registers[41-46]");
						requestedPower *= 100;
						
						// three phase mode
						// distribute request evenly on all phases
						int phasePowerRequest = (int)(requestedPower / 3);
						short highWord = intToShortH(phasePowerRequest);
						short lowWord = intToShortL(phasePowerRequest);
		
						// register 42
						translatedRegisters.put((short)41, highWord);
						// register 43
						translatedRegisters.put((short)42, lowWord);
						// register 44
						translatedRegisters.put((short)43, highWord);
						// register 45
						translatedRegisters.put((short)44, lowWord);
						
						// recalculate for an exact match
						phasePowerRequest = (int)(requestedPower - 2 * phasePowerRequest);
						highWord = intToShortH(phasePowerRequest);
						lowWord = intToShortL(phasePowerRequest);
						// register 46
						translatedRegisters.put((short)45, highWord);
						// register 47
						translatedRegisters.put((short)46, lowWord);
					} else {
						translatedRegisters.put(entry.getKey(), entry.getValue());
					}
				}
				
				// now replace old map
				request.registers = translatedRegisters;
			}
			
			// Debug
			if(request.registers != null && request.registers.size() > 0) {
				for(Map.Entry<Short, Short> entry : request.registers.entrySet()) {
					System.out.println("Register[" + entry.getKey() + "] = " + entry.getValue()); 
				}
			}
			if(request.coils != null && request.coils.size() > 0) {
				for(Map.Entry<Short, Boolean> entry : request.coils.entrySet()) {
					System.out.println("Coil[" + entry.getKey() + "] = " + entry.getValue());
				}
			}
			
			this.remsRequest = request;
		}
	}
	
	/**
	 * Returns the battery currentState
	 * 
	 * @return
	 */
	public BatteryStateData getCurrentStateData() {
		return currentState;
	}
	
	/**
	 * Issue a real power update [W]
	 * 
	 * @param power
	 * @return whether successful or not
	 */
	public boolean setRealPower(long power) {
		log.finest("setRealPower(" + power + ")");
		// check soc bounds
		if( (power > 0 && currentState.stateOfCharge <= configuration.minStateOfCharge) ||
			(power < 0 && currentState.stateOfCharge >= configuration.maxStateOfCharge) ) {
			// limit is reached
			return false;
		}
		realPowerReq = power;
		writeToModbus = true;
		return true;
	}
	
	/**
	 * Emit signal 
	 * 
	 * @param time
	 * @param invalidated
	 * @throws BusException 
	 */
	public void publishScheduleChanged(long time){
		bus.publishScheduleChanged(time);
	}
	
	/**
	 * Returns the scheduler
	 * 
	 * @return
	 */
	public BatteryScheduler getScheduler() {
		return scheduler;
	}
	
	public BatteryCommunication getBusConnection() {
		return bus;
	}
	
	/**
	 * Main method
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		
		// load logger configuration
        try {
			LogManager.getLogManager().readConfiguration(new FileInputStream("logging.properties"));
		} catch (Exception e) {
			System.out.println("Error loading logger configuration:");
			e.printStackTrace();
		}
        
		// load configuration file	
		log.info("Loading configuration: " + args[0]);
		BatteryConfiguration configuration = Json.readFile(args[0], BatteryConfiguration.class);
		
		if(configuration.debug) {
			System.setProperty("com.ghgande.modbus.debug", "true");
		}
		
		// initialize time service
		TimeService timeService;
		try {
			// try to load the given class
			timeService = (TimeService) ClassLoader.getSystemClassLoader().loadClass(configuration.timeProvider).newInstance();
			Time.initialize(timeService);
		} catch (Exception e) {
			e.printStackTrace();
			// could not load the class => use real time
			Time.initialize(new RealTimeService());
		}		
		
		Battery device = new Battery(configuration);
		device.setDriverState(DriverState.On);
		
		// clean up on shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				device.shutdown();
			}
		});
	}
}
