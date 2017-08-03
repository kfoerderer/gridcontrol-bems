package de.fzi.osh.core.timeseries;

/**
 * Interface for time series transformation.
 * 
 * @author K. Foerderer
 *
 * @param <T>
 */
public interface Transformer<T> {
	/**
	 * performs a transformation using the source data and returns the result.
	 * 
	 * @param source
	 * @return
	 */
	public TimeSeries<T> transform(TimeSeries<T> source);
}
