package de.fzi.osh.wamp.schedule;

/**
 * Parameters for flexibility unscheduling:
 * Remove a flexibility from the schedule.
 * 
 * @param id
 * @param power
 * @return {@link SchedulingResponse}
 */
public class UnscheduleFlexibilityRequest {
	/**
	 * Id of flexibility.
	 */
	public int id;
	
	@Override
	public String toString() {
		return UnscheduleFlexibilityRequest.class.getName() + "{id: " + id + "}";
	}
}
