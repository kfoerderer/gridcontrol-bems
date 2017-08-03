package de.fzi.osh.optimization.schedule;

import java.util.UUID;

import de.fzi.osh.types.flexibilities.Flexibility;
import de.fzi.osh.types.flexibilities.Task;

public class ScheduleData {
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
	public Task[] tasks;
	
	/**
	 * An array holding ids of all relevant flexibilities.
	 */
	public Flexibility[] flexibilities;
	
	/**
	 * The constraints as linear inequalities.
	 */
	public String[] constraints;
}
