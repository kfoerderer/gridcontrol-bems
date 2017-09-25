package de.fzi.osh.com.fms;

import de.fzi.osh.types.math.IntInterval;

public class PublicFlexibility {	
	/**
	 * Timestamp the flexibility was computed
	 */
	public long timestamp;
	
	/**
	 * Starting time of first time slot
	 */
	public long startingTime;
	
	/**
	 * Time slot length in seconds
	 */
	public int slotLength;

	/**
	 * Corridor of power values in W for the next day / the remaining hours of the day
	 */
	public IntInterval[] powerCorridor;
	
	/**
	 * Corridor of energy values in Wh for the next day / the remaining hours of the day
	 */
	public IntInterval[] energyCorridor;
}
