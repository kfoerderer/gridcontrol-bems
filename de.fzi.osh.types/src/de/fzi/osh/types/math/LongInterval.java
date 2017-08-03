package de.fzi.osh.types.math;

/**
 * Provides interval using long values as min and max
 * 
 * @author K. Foerderer
 *
 */
public class LongInterval {
	
	public LongInterval() {}
	
	public LongInterval(long min, long max) {
		this.min = min;
		this.max = max;
	}
	
	/**
	 * Lower bound
	 */
	public long min;
	
	/**
	 * Upper bound
	 */
	public long max;
}

