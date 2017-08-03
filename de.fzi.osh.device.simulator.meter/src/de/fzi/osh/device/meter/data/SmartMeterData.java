package de.fzi.osh.device.meter.data;

import java.time.Instant;

import de.fzi.osh.core.oc.DataObject;

/**
 * DataObject for passing observation data
 * 
 * @author K. Foerderer
 *
 */
public class SmartMeterData implements DataObject {
	/**
	 * Instant of measurement.
	 */
	public Instant time;
	/**
	 * Total active power in W/10.
	 */
	public int totalActivePower;
	/**
	 * Total reactive power in W/10.
	 */
	public int totalReactivePower;
	/**
	 * Total active energy in positive direction as Wh/100.
	 */
	public long totalActiveEnergyP;
	/**
	 * Total active energy in negative direction as Wh/100.
	 */
	public long totalActiveEnergyN;
	/**
	 * Alarm flag.
	 */
	public long alarmFlag;
}
