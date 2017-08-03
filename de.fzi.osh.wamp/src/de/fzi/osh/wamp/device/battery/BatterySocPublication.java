package de.fzi.osh.wamp.device.battery;

import java.util.UUID;

/**
 * Data exchange object for SOC publication.
 * 
 * @author K. Foerderer
 *
 */
public class BatterySocPublication {
	/**
	 * Battery UUID.
	 */
	public UUID uuid;
	/**
	 * Instant of publication as UNIX time.
	 */
	public long time;
	/**
	 * State of charge of battery.
	 */
	public byte soc;
	
	/**
	 * SOC in % in terms of effective capacity.
	 */
	public byte effectiveSoc;
	
	/**
	 * Stored Energy which can be discharged from the system by nominal power on the AC side until the system is empty or cannot deliver the nominal power any more. 
	 */
	public long energyUntilEmpty;
	
	/**
	 * Energy which can be charged into the system on nominal power on the AC side until the system is full or cannot deliver the nominal power any more. 
	 */
	public long energyUntilFull;
}
