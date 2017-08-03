package de.fzi.osh.wamp.schedule;

import java.util.NavigableMap;

/**
 * Parameters for a flexibility adaptation request:
 * Change the schedule for an adaptable flexibility. The flexibility in question has to be scheduled beforehand. 
 * For changing the starting time, unshedule the flexibility.
 * <p>
 * May result in flexibilities getting invalidated.
 * </p>
 * The adaption fails for invalid input data. In this case no actions are performed.
 * 
 * @param id of an adaptable flexibility that has previously been scheduled.
 * @param power The power to be overwritten, starting with offset time=0 for the scheduled starting time. The last entry marks the end of the update. Everything after this entry remains unchanged. The power value of the last entry has no meaning.
 * @return {@link SchedulingResponse}
 */
public class AdaptFlexibilityRequest {
	/**
	 * Id of flexibility to adapt.
	 */
	public int id;
	/**
	 * Map of power values.
	 */
	public NavigableMap<Integer, Integer> power;
}
