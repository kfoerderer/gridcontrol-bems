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
	public Instant time;
	public int totalActivePower; //  W / 10
	public int totalReactivePower; // W / 10
	public long totalActiveEnergyP; // Wh / 100
	public long totalActiveEnergyN; // Wh / 100
	public long alarmFlag;
}
