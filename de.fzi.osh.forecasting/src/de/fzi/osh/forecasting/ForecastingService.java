package de.fzi.osh.forecasting;

import java.time.ZonedDateTime;

/**
 * Service providing forecast functionality
 * 
 * @author K. Foerderer
 *
 */
public interface ForecastingService {

	/**
	 * Returns an array of classes this service can provide forecasts for
	 * 
	 * @return
	 */
	public Class<?>[] getCapabilities();
	
	/**
	 * Shortcut for checking certain capabilities
	 * 
	 * @param forecast
	 * @return
	 */
	public boolean canForecast(Class<? extends Forecast> forecast);
	
	/**
	 * Returns a forecast for the given timeperiod
	 * 
	 * @param from starting point
	 * @param to end point
	 * @param clazz
	 * @param argument additional parameters
	 * @return
	 */
	public<T extends Forecast> T getForecast(ZonedDateTime from, ZonedDateTime to, Class<T> clazz, Object argument);
	
}
