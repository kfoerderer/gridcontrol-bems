package de.fzi.osh.device.meter.configuration;

import de.fzi.osh.device.configuration.Configuration;
import de.fzi.osh.time.realtime.RealTimeService;

/**
 * Data structure for smart meter configuration
 * 
 * @author K. Foerderer
 *
 */
public class MeterConfiguration extends Configuration {
	public String timeProvider = RealTimeService.class.getName();
	
	/**
	 * Multiplier for energy values.
	 */
	public int multiplier = 1;
	/**
	 * Sampling interval in ms.
	 */
	public int samplingInterval = 1000;
	/**
	 * Ip of gcu providing meter data.
	 */
	public String gcuAddress = "localhost";
	/**
	 * Port for establishing a connection to gcu.
	 */
	public short gcuPort = 10502; 
	/**
	 * Debuggin
	 */
	public boolean debug = false;
}
