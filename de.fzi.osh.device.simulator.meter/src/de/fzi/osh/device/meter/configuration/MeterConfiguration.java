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
	 * File holding a collection of load profiles.
	 */
	public String loadProfilesFile = "consumption.csv";
	/**
	 * Sign separating values in load profiles file.
	 */
	public String separator = ";";
	/**
	 * Factor for scaling the load profile data.
	 */
	public double scalar = 1;
	/**
	 * Whether the load profile is selected randomly or profile selection follows the order determined by the input file. 
	 */
	public boolean randomLoadProfileSelection = true;
	/**
	 * Probability in %, that the power value is superposed with a random variable.
	 * Set this value to 0 to turn off randomization. 
	 */
	public int randomizationProbability = 50;
	/**
	 * Standard deviation of the normally distributed random variable used in superposition.
	 */
	public int standardError = 60;
	/**
	 * Lower bound for active power (in W) generated through superposition.
	 */
	public int minimumValue = 0;
	/**
	 * Upper bound for active power (in W) generated through superposition.
	 */
	public int maximumValue = 1000000;
	
	/**
	 * Sampling interval in ms.
	 */
	public int samplingInterval = 1000;
}
