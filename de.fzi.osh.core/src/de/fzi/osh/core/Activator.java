package de.fzi.osh.core;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.core.logging.LoggingConfiguration;

public class Activator implements BundleActivator{

	@Override
	public void start(BundleContext context) throws Exception {
		// create service listener, listening for the configuration service
		ServiceListener listener = new ServiceListener() {

			@Override
			public void serviceChanged(ServiceEvent event) {
				switch(event.getType()) {
				case ServiceEvent.REGISTERED:
					
					System.out.println("Setting up logger");
						
					// get service
					@SuppressWarnings("rawtypes") ServiceReference reference = event.getServiceReference();
					@SuppressWarnings("unchecked") ConfigurationService configuration = (ConfigurationService) context.getService(reference);
					
					// set up logger
					LoggingConfiguration loggingConfiguration = configuration.get(LoggingConfiguration.class);
					try {
						// remove std handlers
						LogManager.getLogManager().reset();
						
						if(loggingConfiguration.loggingProperties.length() > 0) {
							// load properties
							try(InputStream inputStream = new FileInputStream(loggingConfiguration.loggingProperties)) {
								LogManager.getLogManager().readConfiguration(inputStream);
							}	
						}
						
						if(loggingConfiguration.handler.length() > 0 && loggingConfiguration.formatter.length() > 0) {
							// setup a new handler
							Handler handler = (Handler)Class.forName(loggingConfiguration.handler).newInstance();
							handler.setFormatter((Formatter)Class.forName(loggingConfiguration.formatter).newInstance());
							handler.setLevel(Level.ALL);
							Logger global = Logger.getLogger("");
							global.addHandler(handler);
						}
						
					} catch (Exception e) {
						System.out.println("Could not set up logger [" + e.toString() + "]");
					}
					
					break;
				}
			}
		};
		
		// register listener
		String filter = "(objectclass=" + ConfigurationService.class.getName() + ")";
		context.addServiceListener(listener, filter);
	}

	@Override
	public void stop(BundleContext context) throws Exception {		
	}

	
	
}
