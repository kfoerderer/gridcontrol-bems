package de.fzi.osh.optimization.schedule;

import java.util.Map;
import java.util.UUID;

import de.fzi.osh.optimization.Solution;
import de.fzi.osh.types.flexibilities.Task;

/**
 * A data structure holding a solution of the target schedule problem.
 * 
 * @author K. Foerderer
 *
 */
public class TargetScheduleSolution extends Solution {
	/**
	 * Optimization interval lower bound, must equal the start of a time slot.
	 */
	public long from;
	/**
	 * Optimization interval upper bound.
	 */
	public long to;
	/**
	 * The target function value
	 */
	public int expectedCumulativeDeviation;
	/**
	 * Secondary target function value
	 */
	public int expectedMaximumDeviation;
	/**
	 * A collection of tasks to execute for each device. Only starting the fields: flexibilityId, startingTime and power hold data. 
	 */
	public Map<UUID, Task[]> tasks;
}
