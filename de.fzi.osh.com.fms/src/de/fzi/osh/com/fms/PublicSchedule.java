package de.fzi.osh.com.fms;

public class PublicSchedule {	
	/**
	 * Epoch second of creation 
	 */
	public long timestamp;
	
	/**
	 * Epoch second of first time slot
	 */
	public long startingTime;
	
	/**
	 * The inflexible consumption in Wh.
	 */
	public int[] consumption;
	
	/**
	 * The inflexible production in Wh (<0).
	 */
	public int[] production;
	
	/**
	 * The flexible consumption in Wh.
	 */
	public int[] flexibleConsumption;
	
	/**
	 * The flexible production in Wh (<0).
	 */
	public int[] flexibleProduction;
	
	/**
	 * Length of a time slot in seconds.
	 */
	public int slotLength;
}
