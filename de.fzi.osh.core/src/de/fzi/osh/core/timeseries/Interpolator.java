package de.fzi.osh.core.timeseries;

/**
 * Interface for interpolation functionality.
 * 
 * @author K. Foerderer
 *
 * @param <T>
 */
public interface Interpolator<T>{
	public T interpolate(long startTime, T startValue, long endTime, T endValue, long time);
}