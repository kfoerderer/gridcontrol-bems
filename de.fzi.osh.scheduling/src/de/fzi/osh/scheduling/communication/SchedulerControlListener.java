package de.fzi.osh.scheduling.communication;

import de.fzi.osh.com.control.ControlCommunicationListener;
import de.fzi.osh.scheduling.Scheduler;
import de.fzi.osh.scheduling.dataobjects.TargetBatteryChargeData;

/**
 * VNB com listener that just passes data to the observer
 * 
 * @author K. Foerderer
 *
 */
public class SchedulerControlListener implements ControlCommunicationListener{

	private Scheduler scheduler;
	
	public SchedulerControlListener(Scheduler scheduler) {
		this.scheduler = scheduler;
	}
	
	@Override
	public void setTargetSOC(int soc, long time) {
		TargetBatteryChargeData data = new TargetBatteryChargeData();
		data.soc = soc;
		data.time = time;
		scheduler.getObserver().update(data);
	}

	@Override
	public void setTargetWh(int wh, long time) {
		TargetBatteryChargeData data = new TargetBatteryChargeData();
		data.wh = wh;
		data.time = time;
		scheduler.getObserver().update(data);
	}
}
