package de.fzi.osh.forecasting.demand;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;

import de.fzi.osh.core.timeseries.TimeSeries;
import de.fzi.osh.data.storage.Column;
import de.fzi.osh.data.storage.Table;
import de.fzi.osh.forecasting.Forecast;

/**
 * Data structure for electricity consumption forecasts
 * 
 * @author K. Foerderer
 *
 */
@Table(name="ElectricityDemandForecasts")
public class ElectricityDemandForecast extends Forecast{
	/**
	 * Timestamp of forecast generation
	 */
	@Column(name="timestamp", declaration="TIMESTAMP NOT NULL")
	public Timestamp timestamp;
	/**
	 * The sourceName's uuid.
	 */
	@Column(name="uuid", declaration="VARCHAR(64)")
	public String guid;
	/**
	 * Timestamp marking begin of first forecast time slot
	 */
	@Column(name="forecastBegin", declaration="TIMESTAMP NOT NULL")
	public Timestamp forecastBegin;
	/**
	 * Timestamp marking end of last forecast time slot
	 */
	@Column(name="forecastEnd", declaration="TIMESTAMP NOT NULL")
	public Timestamp forecastEnd;
	/**
	 * Length of a time slot in seconds
	 */
	@Column(name="timeSlotLength", declaration="INT")
	public int timeSlotLength = 15 * 60;
	/**
	 * Average wattage during time slot t as ;-separated string
	 */
	@Column(name="wattages", declaration="TEXT")
	public String wattages = "";
	
	/**
	 * Converts the database string into an integer array
	 * 
	 * @return
	 */
	public int[] getWattages() {
		String[] splitted = wattages.split(";");
		int[] result = new int[splitted.length];
		
		for(int i = 0; i < result.length; i++) {
			result[i] = Integer.parseInt(splitted[i]);
		}
		
		return result;
	}
	
	/**
	 * Converts an integer array into a string to be stored in the database
	 * 
	 * @param wattages
	 */
	public void setWattages(int[] wattages) {
		this.wattages = "";
		for(int i = 0; i < wattages.length; i++) {
			this.wattages += (i == 0 ? "" : ";") + wattages[i];
		}
	}
	
	/**
	 * Adds a value to the wattages
	 * 
	 * @param wattage
	 */
	public void addWattage(int wattage) {
		wattages += (wattages.length() == 0 ? "" : ";") + wattage;
	}
	
	/**
	 * Converts this forecast into a time series in W.
	 * 
	 * @return
	 */
	public TimeSeries<Integer> getTimeSeries() {
		TimeSeries<Integer> series = new TimeSeries<>(ChronoUnit.SECONDS);
		
		long time = forecastBegin.toInstant().getEpochSecond();
		int[] wattages = getWattages();
		for(int i = 0; i < wattages.length; i++, time += timeSlotLength) {
			series.add(time, wattages[i]);
		}
		
		return series;
	}
}
