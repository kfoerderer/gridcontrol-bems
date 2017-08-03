package de.fzi.osh.forecasting.configuration;

import java.util.UUID;

/**
 * Configuration for the electricity demand forecasting.
 * 
 * This forecasting service can either provide a standard load profile based forecast or
 * a forecast based on averages of previous demand.
 * 
 * @author K. Foerderer
 *
 */
public class ElectricityDemandForecastingConfiguration {
	
	/**
	 * Whether the forecast is derived from the slp or not.
	 * 
	 * <ul>
	 * <li><b>true</b>: SLP is multiplied with multiplier.</li> 
	 * <li><b>false</b>: Use average of last numberOfDays similar days (working days vs Saturday vs Sunday) and multiply with meterScalar.</li>  
	 * </ul>
	 */
	public boolean fromSlp = false;
	
	// Forecast from standard load profile
	
	/**
	 * An appropriate standard load profile for the building 
	 */
	public String standardLoadProfile = "../input/h21.csv";
	
	/**
	 * Multiplier for the standard load profile
	 */
	public double multiplier = 4.4;
	
	/**
	 * For every interval of # seconds a forecast is made
	 */
	public int intervalLength = 15 * 60;
	
	
	// Forecast from historic data	
	
	/**
	 * UUID of meter used as source for demand series.
	 */
	public UUID sourceUUID;
	
	/**
	 * Number of similar days used for computing averages.
	 */
	public int numberOfDays=2;
	
	/**
	 * Scalar multiplied with value to account for meter precision.
	 */
	public double meterScalar = 0.1;
}
