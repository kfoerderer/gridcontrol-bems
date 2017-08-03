package de.fzi.osh.optimization.schedule;

import java.util.Map;
import java.util.UUID;

import de.fzi.osh.com.fms.PublicFlexibility;
import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.optimization.Solution;
import de.fzi.osh.types.flexibilities.Task;

/**
 * A data structure holding a solution of the scheduling problem.
 * 
 * @author K. Foerderer
 *
 */
public class SchedulingSolution extends Solution{
	/**
	 * Optimization interval lower bound
	 */
	public long from;
	/**
	 * Optimization interval upper bound
	 */
	public long to;
	/**
	 * Part of the target function
	 */
	public int expectedElectricityBought;
	/**
	 * Part of the target function
	 */
	public int expectedElectricitySold;
	/**
	 * PublicSchedule resulting from optimization
	 */
	public PublicSchedule schedule;
	/**
	 * Aggregated flexibility for optimized schedule
	 */
	public PublicFlexibility flexibility;
	/**
	 * A collection of tasks to execute for each device. Only using the fields: flexibilityId, startingTime and power hold data. 
	 */
	public Map<UUID, Task[]> tasks;
}
