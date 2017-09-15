package de.fzi.osh.device.meter;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersResponse;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;

import de.fzi.osh.core.data.Json;
import de.fzi.osh.device.Device;
import de.fzi.osh.device.meter.configuration.MeterConfiguration;
import de.fzi.osh.device.meter.data.SmartMeterData;
import de.fzi.osh.device.time.Time;
import de.fzi.osh.time.TimeService;
import de.fzi.osh.time.realtime.RealTimeService;

public class MeteringDevice extends Device<MeteringDevice, MeterConfiguration>  {

	private static Logger log = Logger.getLogger(MeteringDevice.class.getName());
	
	// modbus
	private TCPMasterConnection modbusConnection;
	
	private MeterCommunication communication;
	
	// for preventing frequent reading operations
	private Instant previousPolling = Time.service().nowAsInstant();
	
	public MeteringDevice(MeterConfiguration configuration) {
		super(configuration);
		
		// initialize OC
		controller = new MeteringDeviceController(this);
		observer = new MeteringDeviceObserver(this, controller);
		
		// connect to communication
		communication = new MeterCommunication(this);
		communication.open();

		try {
			modbusConnection = new TCPMasterConnection(InetAddress.getByName(configuration.gcuAddress));
			modbusConnection.setPort(configuration.gcuPort);	
		} catch (UnknownHostException e) {
			log.severe("Battery ip could not be determined using '" + configuration.gcuAddress + "'");
			return;
		}
		
		// create scheduler an measure every $configuration.samplingInterval ms
		Time.service().scheduleAtRate(this, 0, configuration.samplingInterval);
	}

	@Override
	public void run() {
		// the actual driver
		try {
			// prevent frequent reading operations (when executions pile up due to connection issues)
			Instant now = Time.service().nowAsInstant();
			long deltaTime = Duration.between(previousPolling, now).toMillis(); // time since last polling in ms
			if(deltaTime <= configuration.samplingInterval / 2) {
				// execution has piled up
				return;
			}
			previousPolling = now;
		} catch(Exception e) {
			log.severe(e.toString());
		}
			
		// establish connection if device isn't connected
		if(!modbusConnection.isConnected()) {
			try {
				modbusConnection.connect();
			} catch (Exception e) {
				log.severe("Could not establish modbus connection.");
				log.severe(e.toString());
				return;
			}
		}
		
		try {
			// read meter and pass to observer			
			SmartMeterData meterData = new SmartMeterData();
			meterData.time = Time.service().nowAsInstant();
			
			// create transaction
			ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
			
			// read active power and reactive power
			ReadInputRegistersRequest readInputRegistersRequest = new ReadInputRegistersRequest(14, 4);
			// do request
			transaction.setRequest(readInputRegistersRequest);
			try {
				transaction.execute();
			} catch (Exception e) {
				log.severe("Modbus request failed on registers 15-18.");
				log.severe(e.toString());
				return;
			}
			// get response
			ReadInputRegistersResponse readInputRegistersResponse = (ReadInputRegistersResponse) transaction.getResponse();			
			meterData.totalActivePower = ushortToInt(readInputRegistersResponse.getRegisterValue(0), readInputRegistersResponse.getRegisterValue(1));
			meterData.totalActivePower *= configuration.multiplier;
			meterData.totalReactivePower = ushortToInt(readInputRegistersResponse.getRegisterValue(2), readInputRegistersResponse.getRegisterValue(3));
			meterData.totalReactivePower *= configuration.multiplier;
			
			// read active energy 
			readInputRegistersRequest = new ReadInputRegistersRequest(42, 8);
			// do request
			transaction.setRequest(readInputRegistersRequest);
			try {
				transaction.execute();
			} catch (Exception e) {
				log.severe("Modbus request failed on registers 43-50.");
				log.severe(e.toString());
				return;
			}
			// get response
			readInputRegistersResponse = (ReadInputRegistersResponse) transaction.getResponse();
			meterData.totalActiveEnergyP = ushortToLong(readInputRegistersResponse.getRegisterValue(0),
														readInputRegistersResponse.getRegisterValue(1),
														readInputRegistersResponse.getRegisterValue(2),
														readInputRegistersResponse.getRegisterValue(3));
			meterData.totalActiveEnergyP *= configuration.multiplier;
			
			// get response
			//readInputRegistersResponse = (ReadInputRegistersResponse) transaction.getResponse();
			meterData.totalActiveEnergyN = ushortToLong(readInputRegistersResponse.getRegisterValue(4),
														readInputRegistersResponse.getRegisterValue(5),
														readInputRegistersResponse.getRegisterValue(6),
														readInputRegistersResponse.getRegisterValue(7));
			meterData.totalActiveEnergyN *= configuration.multiplier;
			
			// read alarm flag
			readInputRegistersRequest = new ReadInputRegistersRequest(74, 4);
			// do request
			transaction.setRequest(readInputRegistersRequest);
			try {
				transaction.execute();
			} catch (Exception e) {
				log.severe("Modbus request failed on registers 75-78.");
				log.severe(e.toString());
				return;
			}
			// get response
			readInputRegistersResponse = (ReadInputRegistersResponse) transaction.getResponse();
			meterData.alarmFlag = ushortToLong(readInputRegistersResponse.getRegisterValue(0),
														readInputRegistersResponse.getRegisterValue(1),
														readInputRegistersResponse.getRegisterValue(2),
														readInputRegistersResponse.getRegisterValue(3));

			observer.update(meterData);
		} catch(Exception e) {
			log.severe("An exception has been caught!");
			log.severe(e.toString());
			if(configuration.debug == true) {
				e.printStackTrace();	
			}
		} finally {
			// close connection and re-establish during next run
			modbusConnection.close();
		}
	}
	
	public MeterCommunication getBusConnection(){
		return communication;
	}

	/**
	 * Helper for converting register data to integer values.
	 * 
	 * @param value0
	 * @param value1
	 * @return
	 */
	private int ushortToInt(int value0, int value1) {
		return (((int)value0) << 16) | value1;  
	}
	
	/**
	 * Helper for converting register data to long values.
	 * 
	 * @param value0
	 * @param value1
	 * @param value2
	 * @param value3
	 * @return
	 */
	private long ushortToLong(int value0, int value1, int value2, int value3) {
		return (((long)value0) << 48) | (((long)value1) << 32) | (((long)value2) << 16) | value3;  
	}
	
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
		MeterConfiguration configuration = Json.readFile(args[0], MeterConfiguration.class);
		
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
		
		if(configuration.debug) {
			System.setProperty("com.ghgande.modbus.debug", "true");
		}
		
		Device<MeteringDevice, MeterConfiguration> device = new MeteringDevice(configuration);
		
		// clean up on shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				device.shutdown();
			}
		});		
	}
}
