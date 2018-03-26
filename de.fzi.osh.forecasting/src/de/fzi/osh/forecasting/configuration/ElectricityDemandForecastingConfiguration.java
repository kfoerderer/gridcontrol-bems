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
	 * Set of forecasting methods offered.
	 * 
	 * @author Foerderer K.
	 *
	 */
	public static enum ForecastingMethod {
		/**
		 * Empty forecast will be generated
		 */
		None(0), 
		/**
		 * Use a standard load profile.
		 */
		StandardLoadProfile(1),
		/**
		 * Derive forecast from historic data.
		 */
		HistoricData(2);
		
		private int id;
		private ForecastingMethod(int id) {
			this.id = id;
		}
		public int getValue() {
			return id;
		}
		public static ForecastingMethod fromValue(int id) {
			for(ForecastingMethod state: ForecastingMethod.values()) {
				if(state.getValue() == id) {
					return state;
				}
			}
			return ForecastingMethod.None;
		}
	};
	
	/**
	 * Forecasting method to be used.
	 */
	public ForecastingMethod forecastingMethod = ForecastingMethod.HistoricData;
	
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
