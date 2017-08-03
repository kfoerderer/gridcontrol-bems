package de.fzi.osh.wamp.schedule;

import java.util.UUID;

/**
 * A collection holding all basic data for schedule communication.
 * 
 * @author K. Foerderer
 *
 */
public class GetScheduleResponse {
	
	/**
	 * UUID of device providing flexibility.
	 */
	public UUID uuid;
	
	/**
	 * Starting epoch seconds.
	 */
	public long from;
	
	/**
	 * Ending epoch seconds.
	 */
	public long to;
	
	/**
	 * An array with all known ids of tasks running within the given time frame.
	 */
	public int[] tasks;
	
	/**
	 * An array holding ids of all relevant flexibilities.
	 */
	public int[] flexibilities;
	
	/**
	 * The constraints as linear inequalities.
	 */
	public String[] constraints;
}