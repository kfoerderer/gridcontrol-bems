package de.fzi.osh.scheduling.communication;

import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.core.oc.Observer;
import de.fzi.osh.scheduling.Scheduler;
import de.fzi.osh.scheduling.configuration.SchedulerConfiguration;
import de.fzi.osh.scheduling.dataobjects.TargetScheduleData;

/**
 * Fms listener implementation for scheduling service.
 * 
 * @author K. Foerderer
 *
 */
public class SchedulerFmsListener implements de.fzi.osh.com.fms.FmsCommunicationListener{

	private Scheduler scheduler;
	
	/**
	 * Constructor 
	 * 
	 * @param scheduler
	 */
	public SchedulerFmsListener(Scheduler scheduler) {
		this.scheduler = scheduler;	
	}
	
	@Override
	public void updateSchedule(PublicSchedule schedule) {
		Observer<Scheduler, SchedulerConfiguration> observer = scheduler.getObserver();
		
		// create data object
		TargetScheduleData receivedSchedule = new TargetScheduleData();
		
		receivedSchedule.mustUpdate = true;
		receivedSchedule.schedule = schedule;
		
		// notify observer
		observer.update(receivedSchedule);
	}

	@Override
	public void requestSchedule(PublicSchedule schedule) {
		Observer<Scheduler, SchedulerConfiguration> observer = scheduler.getObserver();

		// create data object
		TargetScheduleData receivedSchedule = new TargetScheduleData();
		
		receivedSchedule.mustUpdate = false;
		receivedSchedule.schedule = schedule;
		
		// notify observer
		observer.update(receivedSchedule);
	}
	
}
