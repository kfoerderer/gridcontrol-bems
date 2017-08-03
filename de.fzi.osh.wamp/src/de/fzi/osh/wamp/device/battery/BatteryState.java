package de.fzi.osh.wamp.device.battery;

/**
 * Data exchange object for battery data.
 * 
 * @author K. Foerderer
 *
 */
public class BatteryState {
		
	/**
	 * Tells the battery to have a state of charge of $soc at time $time. The battery stops to offer any flexibilities until $time.
	 * Note that the state of charge can't exceed the minimum and maximum SOC bounds. 
	 */
	public int targetSoc;

	/**
	 * Tells the battery to have a state of charge of $soc at time $time. The battery stops to offer any flexibilities until $time.
	 * Note that the state of charge can't exceed the minimum and maximum SOC bounds. 
	 */
	public long targetSocTime;
	
	/**
	 * State of charge in %.
	 */
	public byte stateOfCharge;
	
	/**
	 * State of health in %.
	 */
	public byte stateOfHealth;
	
	/**
	 * Real power in W. + discharge, - charge
	 */
	public int realPower;
	
	/**
	 * Max charge power in W. This value changes with SOC.
	 */
	public int maxRealPowerCharge;
	
	/**
	 * Max discharge power in W. This value changes with SOC.
	 */
	public int maxRealPowerDischarge;
	
	/**
	 * The battery's nominal capacity in Wh.
	 */
	public int nominalCapacity;	
	
	/**
	 * SOC in % in terms of effective capacity.
	 */
	public int effectiveStateOfCharge;
	
	/**
	 * The battery's effective capacity in Wh.
	 */
	public int effectiveCapacity;
	
	/**
	 * Name of system state.
	 */
	public String systemState;

	/**
	 * System state code as given by bess.
	 */
	public short systemStateCode;
	
	/**
	 * Stored Energy which can be discharged from the system by nominal power on the AC side until the system is empty or cannot deliver the nominal power any more. 
	 */
	public long energyUntilEmpty;
	
	/**
	 * Energy which can be charged into the system on nominal power on the AC side until the system is full or cannot deliver the nominal power any more. 
	 */
	public long energyUntilFull;
	
	/**
	 * Registers SysErrorCode[1-4]
	 */
	public short[] systemErrorCode;
}