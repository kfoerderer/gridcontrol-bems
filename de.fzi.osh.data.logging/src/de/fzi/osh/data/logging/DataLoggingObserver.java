package de.fzi.osh.data.logging;

import de.fzi.osh.core.oc.Controller;
import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.core.oc.Observer;
import de.fzi.osh.data.logging.configuration.DataLoggerConfiguration;

public class DataLoggingObserver extends Observer<DataLogger, DataLoggerConfiguration> {

	public DataLoggingObserver(DataLogger component, Controller<DataLogger, DataLoggerConfiguration> controller) {
		super(component, controller);
	}
	
	@Override
	public void update(DataObject data) {
		controller.update(data);
	}
}
