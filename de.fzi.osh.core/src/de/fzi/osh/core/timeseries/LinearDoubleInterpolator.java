package de.fzi.osh.core.timeseries;

/**
 * Interpolates between two double linearly. If either the start or end value are <i>null</i> the result is 0.
 * 
 * @author K. Foerderer
 *
 */
public class LinearDoubleInterpolator implements Interpolator<Double>{

	@Override
	public Double interpolate(long startTime, Double startValue, long endTime, Double endValue, long time) {
		if(startValue == null || endValue == null) {
			return 0.0;
		}
		
		double factor = (time - startTime) / (double)(startTime - endTime);
		return (startValue * (1-factor) + endValue * factor);
	}

}
