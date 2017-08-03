package de.fzi.osh.wamp.device.meter;

import java.util.UUID;

/**
 * Data exchange object for metering data.
 * 
 * @author K. Foerderer
 *
 */
public class MeterStatePublication {
	/**
	 * Meter uuid.
	 */
	public UUID uuid;
	/**
	 * Instant of publication as UNIX time.
	 */
	public long time;
	/**
	 * Total active power in W / 10 (= value is multiplied with 10).
	 */
	public int totalActivePower;
	/**
	 * Total reactive power in VAr / 10 (= value is multiplied with 10).
	 */
	public int totalReactivePower;
	/**
	 * Total active energy (+) in Wh / 100 (= value is multiplied with 100).
	 */
	public long totalActiveEnergyP;
	/**
	 * Total active energy (-) in Wh / 100 (= value is multiplied with 100);
	 */
	public long totalActiveEnergyN;
	/**
	 * Alarm flag (see gcu documentation).
	 */
	public long alarmFlag;
}
