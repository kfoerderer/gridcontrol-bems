package de.fzi.osh.core;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.time.TimeService;

/**
 * This class simply provides a reference to the time service.
 * (Used for logging)
 * 
 * @author K. Foerderer
 *
 */
@Component(enabled=true,immediate=true)
public class TimeProvider {

	private static TimeService timeService;
	
	@Reference(
			name = "TimeService",
			service = TimeService.class,
			cardinality = ReferenceCardinality.OPTIONAL,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeService"
		)	
	protected synchronized void bindTimeService(TimeService timeService) {
		TimeProvider.timeService = timeService;
	}
	protected synchronized void unbindTimeService(TimeService timeService) {
		TimeProvider.timeService = null;
	}

	public static TimeService getService() {
		return timeService;
	}
}
