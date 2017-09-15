import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.ModbusCoupler;
import com.ghgande.j2mod.modbus.net.ModbusTCPListener;
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalIn;
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;

import de.fzi.osh.com.enabled.implementation.ModbusProcessImage;
import de.fzi.osh.device.battery.data.BatteryStateData;
import de.fzi.osh.device.battery.data.BatteryStateData.BatteryState;
import de.fzi.osh.device.battery.data.BatteryStateData.GridState;
import de.fzi.osh.device.battery.data.BatteryStateData.PhaseMode;
import de.fzi.osh.device.battery.data.BatteryStateData.SystemState;

public class App {
	
	
	/**
	 * Helper for converting register data to integer values.
	 * 
	 * @param value0
	 * @param value1
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
	
	public static void printHelp() {
		System.out.println("Usage: simulator-battery.jar <port> <capacity> <soc> <flex> <3phase>");
		System.out.println("<port>\t\tport to listen on");
		System.out.println("<capacity>\tBattery capacity in Wh");
		System.out.println("<soc>\t\tstarting SOC in %");
		System.out.println("<flex>\t\tflexibility offered (symmetrically) in W");
		System.out.println("<3phase>\t\ttrue if 3 phase mode, false else");
	}
	
	public static void main(String[] args) {
		
		if(args.length != 5) {
			printHelp();
			return;
		}
		
		long charge = 0; // Ws
		long capacity; // Ws
		int soc; // %
		int flexibilityOffered = 2500;
		short port = Modbus.DEFAULT_PORT;
		boolean threePhaseMode = false;
		
		try {
			port = Short.parseShort(args[0]);
			capacity = Long.parseLong(args[1]) * 60 * 60; // Ws
			soc = Integer.parseInt(args[2]); // %
			flexibilityOffered = Integer.parseInt(args[3]);
			threePhaseMode = Boolean.parseBoolean(args[4]);
		} catch(Exception e) {
			printHelp();
			return;
		}
		
		BatteryStateData data = new BatteryStateData();
		
		// set initial data
		data.batteryState = BatteryState.Operating;
		data.cosPhi = 1;
		data.gridState = GridState.Online;
		data.maxRealPowerCharge = flexibilityOffered / 100;
		data.maxRealPowerDischarge = flexibilityOffered / 100;
		data.phaseMode = PhaseMode.Inductive;
		data.reactivePower = 1;
		data.realPower = 2;
		data.realPowerReq = 1;
		data.stateOfCharge = soc;
		data.stateOfHealth = 98;
		data.systemState = SystemState.Off;
		data.systemStateRequest = SystemState.Off;
		
		charge = (int) (capacity * (data.stateOfCharge / 100.0));
		data.energyUntilEmpty = charge / 3600;
		data.energyUntilFull = (capacity - charge) / 3600;
		
		ModbusTCPListener listener = null;
		ModbusProcessImage processImage = new ModbusProcessImage();
				
		// set up modbus interface
		
		SimpleInputRegister gridState = new SimpleInputRegister(data.gridState.getValue());
		SimpleInputRegister realPower = new SimpleInputRegister(data.realPower);
		SimpleInputRegister reactivePower = new SimpleInputRegister(data.reactivePower);
		SimpleInputRegister cosPhi = new SimpleInputRegister(data.cosPhi);
		SimpleInputRegister phaseMode = new SimpleInputRegister(data.phaseMode.getValue());
		SimpleInputRegister maxRealPowerCharge = new SimpleInputRegister(data.maxRealPowerCharge);
		SimpleInputRegister maxRealPowerDischarge = new SimpleInputRegister(data.maxRealPowerDischarge);
		SimpleRegister realPowerReq = new SimpleRegister(data.realPowerReq);
		SimpleRegister powerL1ReqH = new SimpleRegister(0);
		SimpleRegister powerL1ReqL = new SimpleRegister(0);
		SimpleRegister powerL2ReqH = new SimpleRegister(0);
		SimpleRegister powerL2ReqL = new SimpleRegister(0);
		SimpleRegister powerL3ReqH = new SimpleRegister(0);
		SimpleRegister powerL3ReqL = new SimpleRegister(0);
		SimpleInputRegister stateOfCharge = new SimpleInputRegister(data.stateOfCharge);
		SimpleInputRegister stateOfHealth = new SimpleInputRegister(data.stateOfHealth);
		SimpleInputRegister batteryState = new SimpleInputRegister(data.batteryState.getValue());
		SimpleInputRegister energyUntilEmptyH = new SimpleInputRegister(intToShortH((int)data.energyUntilEmpty));
		SimpleInputRegister energyUntilEmptyL = new SimpleInputRegister(intToShortL((int)data.energyUntilEmpty));
		SimpleInputRegister energyUntilFullH = new SimpleInputRegister(intToShortH((int)data.energyUntilFull));
		SimpleInputRegister energyUntilFullL = new SimpleInputRegister(intToShortL((int)data.energyUntilFull));
		SimpleInputRegister systemState = new SimpleInputRegister(data.systemState.getValue());
		SimpleRegister systemStateReq = new SimpleRegister(data.systemStateRequest.getValue());
		
		// dummy registers for GCU
		SimpleRegister reactivePowerReq = new SimpleRegister(0); // register 33
		SimpleInputRegister energyExportedH = new SimpleInputRegister(0); // register 115
		SimpleInputRegister energyExportedL = new SimpleInputRegister(0); // register 116
		SimpleInputRegister energyImportedH = new SimpleInputRegister(0); // register 117
		SimpleInputRegister energyImportedL = new SimpleInputRegister(0); // register 118
		SimpleInputRegister sysErrorCode1 = new SimpleInputRegister(0); // register 236
		SimpleInputRegister sysErrorCode2 = new SimpleInputRegister(0); // register 236
		SimpleInputRegister sysErrorCode3 = new SimpleInputRegister(0); // register 236
		SimpleInputRegister sysErrorCode4 = new SimpleInputRegister(0); // register 236
		
		// dummy variables for registers that are not simulated
		SimpleInputRegister dummyInputRegister = new SimpleInputRegister(0);
		SimpleRegister dummyRegister = new SimpleRegister(0);
		
		// prepare process image
		for(int i = 1; i < 500; i++) {
			processImage.setInputRegister(i, dummyInputRegister);
		}
		for(int i = 1; i < 500; i++) {
			processImage.setRegister(i, dummyRegister);
		}
		processImage.setDigitalIn(35, new SimpleDigitalIn());
		
		processImage.setInputRegister(0, gridState);
		processImage.setInputRegister(1, realPower);
		processImage.setInputRegister(2, reactivePower);
		processImage.setInputRegister(3, cosPhi);
		processImage.setInputRegister(4, phaseMode);		
		processImage.setInputRegister(13, maxRealPowerCharge);
		processImage.setInputRegister(14, maxRealPowerDischarge);
		processImage.setRegister(31, realPowerReq);
		processImage.setRegister(35, powerL1ReqH);
		processImage.setRegister(36, powerL1ReqL);
		processImage.setRegister(37, powerL2ReqH);
		processImage.setRegister(38, powerL2ReqL);
		processImage.setRegister(39, powerL3ReqH);
		processImage.setRegister(40, powerL3ReqL);
		processImage.setInputRegister(125, stateOfCharge);
		processImage.setInputRegister(126, stateOfHealth);		
		processImage.setInputRegister(141, batteryState);
		processImage.setInputRegister(143, energyUntilEmptyH);
		processImage.setInputRegister(144, energyUntilEmptyL);
		processImage.setInputRegister(145, energyUntilFullH);
		processImage.setInputRegister(146, energyUntilFullL);
		processImage.setInputRegister(200, systemState);
		processImage.setRegister(243, systemStateReq);
		
		// for GCU
		processImage.setRegister(32, reactivePowerReq);
		processImage.setInputRegister(114, energyExportedH);
		processImage.setInputRegister(115, energyExportedL);
		processImage.setInputRegister(116, energyImportedH);
		processImage.setInputRegister(117, energyImportedL);
		processImage.setInputRegister(235, sysErrorCode1);
		processImage.setInputRegister(236, sysErrorCode2);
		processImage.setInputRegister(237, sysErrorCode3);
		processImage.setInputRegister(238, sysErrorCode4);
		
		// set up modbus
		ModbusCoupler.getReference().setProcessImage(processImage);
		ModbusCoupler.getReference().setMaster(false);
		ModbusCoupler.getReference().setUnitID(1);
		
		// start listening for connections
		listener = new ModbusTCPListener(1);
		try {
			listener.setAddress(InetAddress.getByName("0.0.0.0"));
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		listener.setPort(port);
		listener.listen();
		
		System.out.println("Battery Simulator");
		System.out.println("3 Phase Mode: " + threePhaseMode);
		System.out.println("Initial Data: " + data );
				
		System.out.println("Epoch Second; Real Power W; SOC %");

		int counter = 0;
		while(true) {				
			
			// wait 1 seconds
			try {
				Thread.sleep(1000);
			} catch(Exception e) {
				
			}		
			
			data.batteryState = BatteryState.fromValue(batteryState.getValue());
			data.cosPhi = cosPhi.getValue();
			data.gridState = GridState.fromValue(gridState.getValue());
			data.maxRealPowerCharge = maxRealPowerCharge.getValue();
			data.maxRealPowerDischarge = maxRealPowerDischarge.getValue();
			data.phaseMode = PhaseMode.fromValue(phaseMode.getValue());
			data.reactivePower = reactivePower.toShort();
			data.realPower = realPower.toShort();
			if(threePhaseMode == false) {
				data.realPowerReq = realPowerReq.toShort();
			} else {
				data.powerL1Req = shortToInt(powerL1ReqH.getValue(), powerL1ReqL.getValue());
				data.powerL2Req = shortToInt(powerL2ReqH.getValue(), powerL2ReqL.getValue());
				data.powerL3Req = shortToInt(powerL3ReqH.getValue(), powerL3ReqL.getValue());
			}
			data.stateOfCharge = stateOfCharge.getValue();
			data.stateOfHealth = stateOfHealth.getValue();
			data.systemStateRequest = SystemState.fromValue(systemStateReq.getValue());
			
			//System.out.println("Test-Debug || reactivePowerReq:" + reactivePowerReq.getValue() + " || counter:" + counter + " -> " + shortToInt(counter / 30, counter));
			
			if(data.systemState == SystemState.Off) {
				System.out.println("Off");
			} else {
				//System.out.println("" + data );
				
				if(threePhaseMode == false) {
					System.out.println(Instant.now().getEpochSecond() + ";" +  data.realPower * 100 + ";" + Math.round((charge / (double)capacity) * 100 * 100) / 100.f + "(=" + data.stateOfCharge + ")");
					
					if(data.realPowerReq > 0) {
						// apply change in "real power"
						realPower.setValue(Math.min(data.realPowerReq, data.maxRealPowerDischarge));
						if(data.realPowerReq > data.maxRealPowerDischarge) {
							System.out.println("WARNING: | realPowerReq | > | maxRealPowerDischarge |");
							realPowerReq.setValue(realPower.toShort());
						}				
					} else if(data.realPowerReq < 0){
						realPower.setValue(Math.max(data.realPowerReq, -data.maxRealPowerCharge));
						if(-data.realPowerReq > data.maxRealPowerCharge) {
							System.out.println("WARNING: | realPowerReq | > | maxRealPowerCharge |");
							realPowerReq.setValue(realPower.toShort());
						}				
					} else {
						realPower.setValue(0);
					}
					charge -= data.realPower * 100; // approx Ws, since an iteration takes about 1 second
					charge = Math.max(charge, 0);
					charge = Math.min(charge, capacity);
					stateOfCharge.setValue((int)((charge / (double)capacity) * 100));
				} else {
					long powerRequest = data.powerL1Req + data.powerL2Req + data.powerL3Req;
					
					System.out.println(Instant.now().getEpochSecond() + ";" + powerRequest + ";" + Math.round((charge / (double)capacity) * 100 * 100) / 100.f + "(=" + data.stateOfCharge + ")");
					
					// These warning messages are for debugging
					if(data.powerL1Req > 0) {
						if(data.powerL1Req > data.maxRealPowerDischarge * 100) {
							System.out.println("WARNING: | powerL1Req | > | maxRealPowerDischarge |");
						}
					} else {
						if(-data.powerL1Req > data.maxRealPowerCharge * 100) {
							System.out.println("WARNING: | powerL1Req | > | maxRealPowerCharge |");
						}
					}
					if(data.powerL2Req > 0) {
						if(data.powerL2Req > data.maxRealPowerDischarge * 100) {
							System.out.println("WARNING: | powerL2Req | > | maxRealPowerDischarge |");
						}
					} else {
						if(-data.powerL2Req > data.maxRealPowerCharge * 100) {
							System.out.println("WARNING: | powerL2Req | > | maxRealPowerCharge |");
						}
					}
					if(data.powerL3Req > 0) {
						if(data.powerL3Req > data.maxRealPowerDischarge * 100) {
							System.out.println("WARNING: | powerL3Req | > | maxRealPowerDischarge |");
						}
					} else {
						if(-data.powerL3Req > data.maxRealPowerCharge * 100) {
							System.out.println("WARNING: | powerL3Req | > | maxRealPowerCharge |");
						}
					}
					// errors won't be fixed
					realPower.setValue((short)(Math.round(powerRequest/100.0)));
					
					charge -= powerRequest; // approx Ws, since an iteration takes about 1 second
					charge = Math.max(charge, 0);
					charge = Math.min(charge, capacity);
					stateOfCharge.setValue((int)((charge / (double)capacity) * 100));
				}
				
				if(data.realPower > 0) {
					if(data.stateOfCharge < 10) {
						System.out.println("WARNING: Deep Discharge!");
					}
				} else if(data.realPower < 0){
					if(data.stateOfCharge > 90) {
						System.out.println("WARNING: High Charge!");
					}
				}			
				
				data.energyUntilEmpty = charge / 3600;
				data.energyUntilFull = (capacity - charge) / 3600;
				energyUntilEmptyH.setValue(intToShortH((int)data.energyUntilEmpty));
				energyUntilEmptyL.setValue(intToShortL((int)data.energyUntilEmpty));
				energyUntilFullH.setValue(intToShortH((int)data.energyUntilFull));
				energyUntilFullL.setValue(intToShortL((int)data.energyUntilFull));
				
				// for GCU
				sysErrorCode1.setValue(1);
				sysErrorCode2.setValue(2);
				sysErrorCode3.setValue(3);
				sysErrorCode4.setValue(4);
				energyExportedH.setValue(0);
				energyExportedL.setValue(counter);
				energyImportedH.setValue(0);
				energyImportedL.setValue(counter);
				counter++;
			}
			if(data.systemState != data.systemStateRequest) {
				System.out.println("Changed system state to '" + data.systemStateRequest + "'.");
				data.systemState = data.systemStateRequest;
				systemState.setValue(data.systemState.getValue());
			}
		}
	}
}
