package de.fzi.osh.optimization.schedule;

import java.util.Map;
import java.util.UUID;

import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.optimization.Problem;

/**
 * Data structure holding all data needed for schedule optimization. 
 * 
 * @author K. Foerderer
 *
 */
public class TargetScheduleProblem extends Problem{
	/**
	 * Optimization interval lower bound.
	 */
	public long from;
	/**
	 * Optimization interval upper bound.
	 */
	public long to;
	/**
	 * Marks begin of current time slot.
	 */
	public long currentSlotBegin;
	/**
	 * Time slot length in seconds.
	 */
	public int slotLength;
	/**
	 * A forecast of the electricity demand in Wh.
	 */
	public int[] electricityDemand;
	/**
	 * A forecast of the solar power production in Wh. 
	 */
	public int[] electricityProduction;
	/**
	 * A collection of all available schedules.
	 * 
	 * device -> tasks & offered flexibilities
	 */
	public Map<UUID, ScheduleData> schedules;
	/**
	 * Target schedule.
	 */
	public PublicSchedule targetSchedule;
	/**
	 * Only flexibilities starting at or after (now + buffer) will be turned into tasks. This is for consideration of solving and communication time needs.
	 */
	public int optimizationTimeBuffer;
	/**
	 * For adaptable flexibilities this buffer is applied, to leave some space for adaptation.
	 */
	public int flexibilityAdaptionBuffer;
}
