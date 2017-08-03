package de.fzi.osh.core.timeseries;

/**
 * Floor function.
 * 
 * @author K. Foerderer
 *
 * @param <T>
 */
public class FloorInterpolator<T> implements Interpolator<T> {

	@Override
	public T interpolate(long startTime, T startValue, long endTime, T endValue, long time) {
		return startValue;
	}
	
}
