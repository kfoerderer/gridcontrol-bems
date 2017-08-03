package de.fzi.osh.scheduling.dataobjects;

import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.core.oc.DataObject;

/**
 * Data object for incoming schedule requests. 
 * 
 * @author K. Foerderer
 *
 */
public class TargetScheduleData implements DataObject{
	/**
	 * Whether the schedule has to be updated or not
	 */
	public boolean mustUpdate;
	/**
	 * PublicSchedule that has been received.
	 */
	public PublicSchedule schedule;
}
