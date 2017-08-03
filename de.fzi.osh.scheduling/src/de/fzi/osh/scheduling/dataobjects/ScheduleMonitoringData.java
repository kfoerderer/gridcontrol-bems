package de.fzi.osh.scheduling.dataobjects;

import de.fzi.osh.core.oc.DataObject;

/**
 * Data object for adapting schedules. An object of this type is generated periodically and provides data for fast schedule adaptations.
 * Do NOT start any re-optimizations within the thread this object is received.
 * 
 * Note: object data is set within observer
 * 
 * @author K. Foerderer
 *
 */
public class ScheduleMonitoringData implements DataObject{
	/**
	 * Average consumption (in W) since last adaptation.
	 */
	public double consumption;
	/**
	 * Average production (in W) since last adaptation. (Values < 0)
	 */
	public double production;
	/**
	 * Average battery power (in W) since last adaptation. (> 0 => battery is charging, < 0 => battery is discharging)
	 */
	public double battery;
	/**
	 * PublicSchedule for this moment in time (in W).
	 */
	public double schedule;
}
