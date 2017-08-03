package de.fzi.osh.wamp.schedule;

import java.util.Map;
import java.util.NavigableMap;

/**
 * Parameters for flexibility scheduling:
 * Schedules a flexibility. Overwrites previous scheduling of flexibility with identical id if the corresponding task has not started yet.
 * 
 * @param id
 * @param startingTime Epoch second count of starting time.
 * @param power Mapping second -> power. Second 0 is assumed to be mapped to power 0 if not present. Must end in zero.
 * @return SchedulingResult
 */
public class ScheduleFlexibilityRequest {
	/**
	 * Id of flexibility.
	 */
	public int id;
	/**
	 * Time to start the task.
	 */
	public long startingTime;
	/**
	 * Task load profile.
	 */
	public NavigableMap<Integer,Integer> power;
	
	@Override
	public String toString() {
		String powers = "";
		for(Map.Entry<Integer, Integer> entry : power.entrySet()) {
			powers += entry.getKey() + ":" + entry.getValue() + ",";
		}
		powers = powers.substring(0, powers.length()-1);
		return ScheduleFlexibilityRequest.class.getName() + "{id: " + id + ", startingTime: " + startingTime + ", power: {" + powers + "}}";
	}
}
