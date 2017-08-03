package de.fzi.osh.core.timeseries;

/**
 * Interpolates between two integers linearly. If either the start or end value are <i>null</i> the result is 0.
 * 
 * @author K. Foerderer
 *
 */
public class LinearIntegerInterpolator implements Interpolator<Integer>{

	@Override
	public Integer interpolate(long startTime, Integer startValue, long endTime, Integer endValue, long time) {
		if(startValue == null || endValue == null) {
			return 0;
		}
		
		double factor = (time - startTime) / (double)(startTime - endTime);
		return (int)(startValue * (1-factor) + endValue * factor);
	}

}
