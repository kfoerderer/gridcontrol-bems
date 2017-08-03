package de.fzi.osh.wamp.schedule;

import java.util.UUID;

/**
 * Interface for providing task and flexibility data.
 * 
 * @author K. Foerderer
 *
 */
public class ScheduleTopics {
	
	private String prefix;
	
	/**
	 * Constructor
	 * 
	 * Sets up all the topic names.
	 * 
	 * @param topicPrefix Topic topicPrefix, e.g. "fzi.osh".
	 * @param uuid UUID of this device or null to get wildcard topics.
	 */
	public ScheduleTopics(String prefix) {
		this.prefix = prefix;
	}
	
	/**
	 * Topic used for schedule requests.
	 */
	public final String getSchedule(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".schedule.get";
	}
	
	/**
	 * Topic for signalling schedule changes.
	 * 
	 * @param uuid uuid of device that changed its schedule or <i>null</> to retrieve the wildcard topic.
	 */
	public final String scheduleChanged(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".schedule.changed";
	}
	
	/**
	 * Topic used for task requests.
	 */
	public final String getTask(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".schedule.task.get";
	}
	
	/**
	 * Topic used for flexibility requests.
	 */
	public final String getFlexibility(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".schedule.flexibility.get";
	}
	
	/**
	 * Topic used for requesting adaptations to scheduled flexibilities.
	 */
	public final String adaptFlexibility(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".schedule.flexibility.adapt";
	}
	
	/**
	 * Topic used for scheduling flexibilities.
	 */
	public final String scheduleFlexibility(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".schedule.flexibility.schedule";
	}
	
	/**
	 * Topic for removing a task created from a flexibility.
	 */
	public final String unscheduleFlexibility(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".schedule.flexibility.unschedule";
	}
}
