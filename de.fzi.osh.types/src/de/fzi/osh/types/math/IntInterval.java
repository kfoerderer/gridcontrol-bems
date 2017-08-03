package de.fzi.osh.types.math;

/**
 * Provides interval using integer values
 * 
 * @author K. Foerderer
 *
 */
public class IntInterval {
	
	public IntInterval() {}
	
	public IntInterval(int min, int max) {
		this.min = min;
		this.max = max;
	}
	
	/**
	 * Lower bound
	 */
	public int min;
	
	/**
	 * Upper bound
	 */
	public int max;
	
	@Override
	public String toString() {
		return "[" + min + ", " + max + "]";
	}
}