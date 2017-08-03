package de.fzi.osh.optimization.schedule;

import java.util.Map;
import java.util.UUID;

import de.fzi.osh.optimization.Problem;

/**
 * Data structure holding all data needed for schedule optimization. 
 * 
 * @author K. Foerderer
 *
 */
public class SchedulingProblem extends Problem{
	/**
	 * Optimization interval lower bound.
	 */
	public long from;
	/**
	 * Optimization interval upper bound.
	 */
	public long to;
	/**
	 * Only flexibilities starting at or after (now + buffer) will be turned into tasks. This is for consideration of solving and communication time needs.
	 * This should actually never be of an issue, since this optimization only changes tasks starting at or after $from which should be a future point in time.
	 * Nevertheless, if $from < now + buffer then $from = now + buffer is applied.
	 */
	public int optimizationTimeBuffer;
	/**
	 * A forecast of the electricity demand in Wh.
	 */
	public int[] electricityDemand;
	/**
	 * A forecast of the solar power production in Wh. Power values < 0.
	 */
	public int[] electricityProduction;
	/**
	 * Time slot length in seconds.
	 */
	public int slotLength;
	/**
	 * For adaptable flexibilities this buffer (in Ws) is applied, to leave some space for adaptation.
	 */
	public int flexibilityAdaptionBuffer;
	/**
	 * A collection of all available schedules
	 * 
	 * device -> tasks & offered flexibilities
	 */
	public Map<UUID, ScheduleData> schedules;
}
