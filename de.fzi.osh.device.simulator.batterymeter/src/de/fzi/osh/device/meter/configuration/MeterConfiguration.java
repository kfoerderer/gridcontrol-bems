package de.fzi.osh.device.meter.configuration;

import java.util.UUID;

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
	 * File holding a collection of load profiles.
	 */
	public UUID batteryUUID;
	
	/**
	 * Sampling interval in ms.
	 */
	public int samplingInterval = 1000;
}
