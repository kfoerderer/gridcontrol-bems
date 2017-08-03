package de.fzi.osh.data.logging;

import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.core.component.OshComponent;
import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.data.logging.configuration.DataLoggerConfiguration;
import de.fzi.osh.data.storage.timeseries.TimeSeriesStorageService;
import de.fzi.osh.wamp.configuration.WampConfiguration;

@Component(enabled=true,immediate=true)
public class DataLogger extends OshComponent<DataLogger, DataLoggerConfiguration>{

	private static Logger log = Logger.getLogger(DataLogger.class.getName());
	
	private static ConfigurationService configurationService;	
	
	private static TimeSeriesStorageService timeSeriesStorageService;
	
	private DataLoggerCommunication bus;
	
	public DataLogger() {
		controller = new DataLoggingController(this);
		observer = new DataLoggingObserver(this, controller);
	}
	
	@Activate
	public void activate() {
		Thread thread = new Thread(this);
		thread.start();
	}
	
	@Override
	public void run() {
		log.info("Starting data logger");
		
		// get configuration		
		configuration = configurationService.get(DataLoggerConfiguration.class);
		WampConfiguration wampConfiguration = configurationService.get(WampConfiguration.class);
		
		observer.initialize();
		controller.initialize();			

		// connect to bus
		bus = new DataLoggerCommunication(this, wampConfiguration);
		bus.open();
	}
	
	
	public static TimeSeriesStorageService getTimeSeriesStorageService() {
		return timeSeriesStorageService;
	}
	
	
	@Reference(
			name = "TimeSeriesStorageService",
			service = TimeSeriesStorageService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeSeriesStorageService"
		)	
	protected synchronized void bindTimeSeriesStorageService(TimeSeriesStorageService dataStorageService) {
		DataLogger.timeSeriesStorageService = dataStorageService;
	}
	protected synchronized void unbindTimeSeriesStorageService(TimeSeriesStorageService dataStorageService) {
		DataLogger.timeSeriesStorageService = null;
	}

	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		DataLogger.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		DataLogger.configurationService = null;
	}
}
