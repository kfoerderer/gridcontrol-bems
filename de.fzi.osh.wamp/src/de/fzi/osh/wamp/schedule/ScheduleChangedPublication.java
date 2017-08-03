package de.fzi.osh.wamp.schedule;

import java.util.UUID;

/**
 * The schedule has changed. Tasks and/or flexibilities have been added, changed or invalidated.
 * 
 * @param from Epoch second count of earliest change.
 */
public class ScheduleChangedPublication {
	/**
	 * UUid of sender.
	 */
	public UUID uuid;
	/**
	 * Time stamp marking earliest change.
	 */
	public long from;
}
