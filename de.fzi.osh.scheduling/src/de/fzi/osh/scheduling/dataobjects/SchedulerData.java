package de.fzi.osh.scheduling.dataobjects;

import java.util.NavigableMap;

import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.core.oc.DataObject;

/**
 * Data needed by scheduler that should persist a restart
 * 
 * @author K. Foerderer
 *
 */
public class SchedulerData implements DataObject{
	/**
	 * Whether enabled or not
	 */
	public boolean GCUEnabled = true;
	/**
	 * BESS charge target
	 */
	public TargetBatteryChargeData targetChargeData = null;
	/**
	 * The latest target schedule
	 */
	public NavigableMap<Long, PublicSchedule> targetSchedule = null;
	/**
	 * End of the most recent target schedule optimization as epoch second.
	 */
	public long mostRecentTargetScheduleOptimization = 0;
	/**
	 * Time of the most recent schedule update publication. This is used to prevent schedule update flooding.
	 */
	public long latestScheduleUpdatePublication = 0;
	/**
	 * Time of the most recent initial schedule publication. This is needed on startup to make sure a schedule has been published.
	 */
	public long latestInitialSchedulePublication = 0;
	/**
	 * Whether the initial schedule and flexibilities have to be published or not
	 */
	public SchedulePublishingData incompleteInitialPublication = null;
	/**
	 * Whether an updated schedule and updated flexibilities have to be published or not
	 */
	public SchedulePublishingData incompleteUpdatePublication = null;	
}
