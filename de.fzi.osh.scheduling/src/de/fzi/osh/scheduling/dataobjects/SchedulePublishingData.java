package de.fzi.osh.scheduling.dataobjects;

import de.fzi.osh.core.oc.DataObject;

/**
 * Event data for schedule publishing.
 * 
 * @author K. Foerderer
 *
 */
public class SchedulePublishingData implements DataObject{
	/**
	 * Starting time of schedule as epoch second.
	 */
	public long from;
	/**
	 * Ending time of schedule as epoch second.
	 */
	public long to;
	/**
	 * Whether this is an initial publication or an update.
	 */
	public boolean initial;
}
