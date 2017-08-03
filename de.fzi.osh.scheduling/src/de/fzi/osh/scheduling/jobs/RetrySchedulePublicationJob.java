package de.fzi.osh.scheduling.jobs;

import java.util.logging.Logger;

import de.fzi.osh.scheduling.Scheduler;
import de.fzi.osh.scheduling.dataobjects.SchedulerData;

/**
 * This code is executed periodically after a failed schedule or flexibility upload attempt
 * 
 * @author K. Foerderer
 *
 */
public class RetrySchedulePublicationJob implements Runnable{

	private static Logger log = Logger.getLogger(RetrySchedulePublicationJob.class.getName());
	
	private Scheduler component;
	
	public RetrySchedulePublicationJob(Scheduler scheduler) {
		this.component = scheduler;
	}
	
	@Override
	public void run() {
		SchedulerData data = component.getData();

		// pass optimization to queue
		if(null != data.incompleteUpdatePublication) {
			// filter old tasks
			if(data.incompleteUpdatePublication.to <= Scheduler.getTimeService().now()) {
				log.info("Dropping old information on update publication.");
				data.incompleteUpdatePublication = null;
			} else {
				component.getOptimizationQueue().queueSchedulePublication(data.incompleteUpdatePublication);
			}
		}
		if(null != data.incompleteInitialPublication) {
			// filter old tasks
			if(data.incompleteInitialPublication.to <= Scheduler.getTimeService().now()) {
				log.info("Dropping old information on initial publication.");
				data.incompleteInitialPublication = null;
			} else {
				component.getOptimizationQueue().queueSchedulePublication(data.incompleteInitialPublication);	
			}
		}
	}

}
