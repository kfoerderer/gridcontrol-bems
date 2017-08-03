package de.fzi.osh.scheduling.communication;

import de.fzi.osh.core.oc.Observer;
import de.fzi.osh.scheduling.Scheduler;
import de.fzi.osh.scheduling.configuration.SchedulerConfiguration;
import de.fzi.osh.scheduling.dataobjects.EnabledData;

/**
 * Enabled listener implementation for scheduling service.
 * 
 * @author K. Foerderer
 *
 */
public class SchedulerEnabledListener implements de.fzi.osh.com.enabled.EnabledListener{

	private Scheduler scheduler;
	
	/**
	 * Constructor
	 * 
	 * @param scheduler
	 */
	public SchedulerEnabledListener(Scheduler scheduler) {
		this.scheduler = scheduler;
	}
	
	@Override
	public void enable() {
		Observer<Scheduler, SchedulerConfiguration> observer = scheduler.getObserver();
		
		// create data object
		EnabledData signal = new EnabledData();
		
		signal.enabled = true;
		
		observer.update(signal);
	}

	@Override
	public void disable() {
		Observer<Scheduler, SchedulerConfiguration> observer = scheduler.getObserver();
		
		// create data object
		EnabledData signal = new EnabledData();
		
		signal.enabled = false;
		
		observer.update(signal);
	}

}
