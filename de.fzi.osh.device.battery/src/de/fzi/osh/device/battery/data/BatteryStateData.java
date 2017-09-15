package de.fzi.osh.device.battery.data;

import de.fzi.osh.core.oc.DataObject;

/**
 * DataObject for passing observation data.
 * 
 * Modbus register numbers are given in the descriptions of each value. 
 * 
 * <p>
 * <h1>[!] Keep in mind that Offset/Ref = Register - 1.</h1>
 * Hence, in order to access register 1 a call with offset/ref = 0 is needed. 
 * </p>
 * 
 * @author K. Foerderer
 *
 */
public class BatteryStateData implements DataObject {
	
	/**
	 * constructor
	 */
	public BatteryStateData() {
		systemErrorCode = new short[4];
	}

	/**
	 * Mapping of battery interface grid states
	 * 
	 * @author K. Foerderer
	 *
	 */
	public static enum GridState {
		Off(0), IslandMode(1), Online(2), Error(3);
		
		private int id;
		private GridState(int id) {
			this.id = id;
		}
		public int getValue() {
			return id;
		}
		public static GridState fromValue(int id) {
			for(GridState state: GridState.values()) {
				if(state.getValue() == id) {
					return state;
				}
			}
			return GridState.Error;
		}
	};
	
	/**
	 * Mapping of battery interface phase shift
	 * 
	 * @author K. Foerderer
	 *
	 */
	public static enum PhaseMode {
		Capacitive(0), Inductive(1);
		
		private int id;
		private PhaseMode(int id) {
			this.id = id;
		}
		public int getValue() {
			return id;
		}
		public static PhaseMode fromValue(int id) {
			for(PhaseMode mode: PhaseMode.values()) {
				if(mode.getValue() == id) {
					return mode;
				}
			}
			return PhaseMode.Capacitive;
		}
	}
	
	/**
	 * Mapping of battery state values
	 * 
	 * @author K. Foerderer
	 *
	 */
	public static enum BatteryState {
		Off(0), StartUp(1), Balancing(2), Ready(3), Operating(4), Error(6);
		
		private int id;
		private BatteryState(int id) {
			this.id = id;
		}
		public int getValue() {
			return id;
		}
		public static BatteryState fromValue(int id) {
			for(BatteryState state: BatteryState.values()) {
				if(state.getValue() == id) {
					return state;
				}
			}
			return BatteryState.Error;
		}
	}
	
	/**
	 * State of battery storage system.
	 * 
	 * @author K. Foerderer
	 *
	 */
	public static enum SystemState {
		Off(0), Balancing(1), Auto(2), IslandMode(3), LineCommutated(4), Standby(10), Sleep(20), Error(30);
		
		private int id;
		private SystemState(int id) {
			this.id = id;
		}
		public int getValue() {
			return id;
		}
		public static SystemState fromValue(int id) {
			for(SystemState state: SystemState.values()) {
				if(state.getValue() == id) {
					return state;
				}
			}
			return SystemState.Error;
		}
	}
	
	/* input */
	/**
	 * State of grid.
	 * 
	 * [Register 1]
	 */
	public GridState gridState;
	/**
	 * Real power in 100W.
	 * <ul>
	 * 	<li> + discharge</li>
	 * 	<li> - charge</li>
	 * </ul>
	 * 
	 * [Register 2]
	 */
	public short realPower;
	/**
	 * Reactive power in 100Var.
	 * 
	 * [Register 3]
	 */
	public short reactivePower;
	/**
	 * Cos Phi [0.01] absolute value. Use phaseMode for information on sign.
	 * 
	 * [Register 4]
	 */
	public int cosPhi;
	/**
	 * Phase mode, indicated direction of cosPhi.
	 * 
	 * [Register 5]
	 */
	public PhaseMode phaseMode;
	/**
	 * Maximum real power charging in 100W. Value is not constant.
	 * 
	 * [Register 14]
	 */
	public int maxRealPowerCharge;
	/**
	 * Maximum real power discharging in 100W. Value is not constant.
	 * 
	 * [Register 15]
	 */
	public int maxRealPowerDischarge;
	/**
	 * State of charge in %. (Actual SOC, avoid depth discharge)
	 * 
	 * [Register 126]
	 */
	public int stateOfCharge;
	/**
	 * Battery health in %.
	 * 
	 * [Register 127]
	 */
	public int stateOfHealth;
	/**
	 * Battery state.
	 * 
	 * [Register 142]
	 */
	public BatteryState batteryState;
	/**
	 * Stored Energy which can be discharged from the system by nominal power on the AC side 
	 * until the system is empty or cannot deliver the nominal power any more.
	 * 
	 * [Register 144: (high word) [Wh] unsigned]
	 * [Register 145: (low word) [Wh] unsigned]
	 */
	public long energyUntilEmpty;
	/**
	 * Energy which can be charged into the system on nominal power on the AC side 
	 * until the system is full or cannot deliver the nominal power any more.
	 * 
	 * [Register 146: (high word) [Wh] unsigned]
	 * [Register 147: (low word) [Wh] unsigned]
	 */
	public long energyUntilFull;
	/**
	 * State of system.
	 * 
	 * [Register 201]
	 */
	public SystemState systemState;
	/**
	 * SysErrCode1 [Register 236, Func 4]		
	 *	StoraXe System Error as Bit field
	 *	Bit 0: Battery in error state (BatteryState == Error)
	 *	Bit 1: Inverter in error state
	 *	Bit 2 - 7: reserved
	 *	Bit 8: SRS string voltage difference too high
	 *	Bit 9: SRS timeout
	 *	Bit 10: SRS did not respond to contactor command
	 *	Bit 11: Unexpected contactor status of SRS
	 *	Bit 12 - 14: reserved
	 *	Bit 15: Inverter/Grid not ready
	 *
	 * SysErrCode2 [Register 237, Func 4] 
	 * 	StoraXe System Error as Bit field
	 * 	Bit 0: Number of SRS incorrect
	 * 	Bit 1: reserved
	 * 	Bit 2: reserved	
	 * 	Bit 3: SRS communication problem
	 * 	Bit 4: Missing SXS configuration
	 * 	Bit 5: Inverter offline
	 * 	Bit 6: Forced system shutdown
	 * 	Bit 7: N/PE error
	 * 	Bit 8: CO sensor
	 * 	Bit 9: Water sensor
	 * 	Bit 10: Emergency button
	 * 	Bit 11: CO2 extinguisher: Alarm
	 * 	Bit 12: (reserved)
	 * 	Bit 13: (reserved)
	 * 	Bit 14: (reserved)
	 * 	Bit 15: CO2 extinguisher: actuated
	 *
	 * SysErrCode3 [Register 238, Func 4]
	 * 	StoraXe System Error as Bit field
	 * 	Bit 0: Transformer over temperature
	 * 	Bit 1: Transformer temperature sensor error
	 * 	Bit 2: Watchdog timeout
	 * 	Bit 3 - 15: reserved
	 *
	 * SysErrCode4 [Regsiter 239, Func 4]
	 * 	StoraXe System Error as Bit field
	 * 	Bit 1 - 13: reserved
	 * 	Bit 14: System locked
	 * 	Bit 15: SRS precharge fuse failed or SRS not connected to DC bus
	 *
	 */
	public short[] systemErrorCode;
	
	/* output */
	/**
	 * Target real power value in 100W.
	 * <ul>
	 *  <li> + = discharge </li>
	 *  <li> - charge </li>
	 * </ul>
	 *  
	 *  [Register 32]
	 *  <p><b>[!]Use either realPowerReq or powerL1Req/powerL2Req/powerL3Req</b></p>
	 */
	public short realPowerReq; 
	
	/**
	 * Requested real power phase L1 [W] in a two’s complement.
	 * Power flow in direction of the battery is negative.
	 * 
	 * [Register 36: high word [W]]
	 * [Register 37: low word [W]]
	 * 
	 * <p>Registers 36..41 only valid for inverters with independent three phase power control. 
	 * Always access high word first to ensure data consistency. Requests are accepted as soon as the low word is set.</p>
	 * 
	 * <p><b>[!]Use either realPowerReq or powerL1Req/powerL2Req/powerL3Req</b></p>
	 */
	public int powerL1Req;
	/**
	 * Requested real power phase L2 [W] in a two’s complement.
	 * Power flow in direction of the battery is negative.
	 * 
	 * [Register 38: high word [W]]
	 * [Register 39: low word [W]]
	 * 
	 * <p>Registers 36..41 only valid for inverters with independent three phase power control. 
	 * Always access high word first to ensure data consistency. Requests are accepted as soon as the low word is set.</p>
	 * 
	 * <p><b>[!]Use either realPowerReq or powerL1Req/powerL2Req/powerL3Req</b></p>
	 */
	public int powerL2Req;
	/**
	 * Requested real power phase L3 [W] in a two’s complement.
	 * Power flow in direction of the battery is negative.
	 * 
	 * [Register 40: high word [W]]
	 * [Register 41: low word [W]]
	 * 
	 * <p>Registers 36..41 only valid for inverters with independent three phase power control. 
	 * Always access high word first to ensure data consistency. Requests are accepted as soon as the low word is set.</p>
	 * 
	 * <p><b>[!]Use either realPowerReq or powerL1Req/powerL2Req/powerL3Req</b></p>
	 */
	public int powerL3Req;
	
	/**
	 * Requested system state.
	 * 
	 * [Register 244]
	 * 
	 * <p>
	 * Reg. 244 enables to request for a new system state. 
	 * The balancing-state initiates parallel string balancing between multiple parallelly connected strings to balance their string voltages. 
	 * This state is automatically called when switching from “0 = off” to state 2, 3, 4, 10 or 20 and is not necessary for normal operation.
	 * </p>
	 * 
	 * <p>
	 * For systems with island and emergency power option the standard operating state is “2 = auto”. 
	 * The system states “3 = island mode” and “4 = line-commutated” allow to manually selecting the system state. 
	 * In auto state, the system operates line-commutated and automatically switches to island state in case of a mains power failure.
	 * </p>
	 * 
	 * <p>
	 * State “10 = standby” puts the system in a stand-by operating state:
	 * When switching to system state “standby”, the system starts (if it is not already running in state = 2, 3 or 4), boosts the DC link 
	 * and then connects to AC grid. Subsequently, the system stops boosting the DC link from the batteries and supplies the DC link over a 
	 * bridge rectifier from AC grid. This state conserves battery power, reduces strain on the bat-tery and thus enhances the battery lifetime. 
	 * Furthermore this state allows to quickly switching back to normal operating states 2, 3 or 4.
	 * </p>
	 * 
	 * <p>
	 * The system state “sleep” enables to reduce the stand-by power and still being able to power up quickly. The battery string operates in this state
	 * while the inverter controller is sleeping. The DC contactors between battery and inverter are closed in order to boost the DC link quickly.
	 * </p>
	 * 
	 * Not all specified states are available for all systems, e.g. depending on the inverter features/type.
	 */
	public SystemState systemStateRequest;
	
	@Override
	public String toString() {
		return "GridState:" + gridState.toString() + ", Real Power:" + realPower + " (Req: " + realPowerReq + "), Reactive Power:" + reactivePower + 
				", Cos Phi:" + cosPhi + ", Phase Mode:" + phaseMode.toString() + ", Max Real Power Charge:" + maxRealPowerCharge + 
				", Max Real Power Discharge:" + maxRealPowerDischarge + ", State Of Charge:" + stateOfCharge + 
				", State Of Health:" + stateOfHealth + ", Battery State:" +batteryState.toString();
	}
}
