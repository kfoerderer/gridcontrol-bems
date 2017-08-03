package de.fzi.osh.forecasting.configuration;

public class SolarPowerForecastingConfiguration {	
	/**
	 * For every interval of # seconds a forecast is made
	 */
	public int intervalLength = 15 * 60;
	/**
	 * Scalar multiplied with value to account for meter precision.
	 */
	public double scalar = 0.1;
}
