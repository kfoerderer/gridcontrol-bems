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
	 * Time of measurement as instant.
	 */
	public Instant time;
	/**
	 * Sum of active power on all phases in W / 10.
	 */	
	public int totalActivePower;
	/**
	 *  Sum of reactive power on all phases in W / 10.
	 */
	public int totalReactivePower; 
	/**
	 *  Sum of active (positive) energy on all phases in Wh / 100.
	 */
	public long totalActiveEnergyP;
	/**
	 *  Sum of active (negative) energy on all phases in Wh / 100.
	 */
	public long totalActiveEnergyN;
	/**
	 * Alarm flag, see documentation.
	 */
	public long alarmFlag;
}
