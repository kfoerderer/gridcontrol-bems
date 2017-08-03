package de.fzi.osh.core.logging;

/**
 * Logger configuration
 * 
 * @author K. Foerderer
 *
 */
public class LoggingConfiguration {
	/**
	 * Handler to use for logging. Default handler passes all log entries to Osgi logging service.
	 */
	public String handler = "de.fzi.osh.core.logging.OsgiHandler";
	/**
	 * The formatter to be used for logging. Default formatter is adapted for Osgi loggin.
	 */
	public String formatter = "de.fzi.osh.core.logging.OsgiLogFormatter";
	/**
	 * Properties file for additional logger configuration, that is not prevented by Osgi class loading.
	 */
	public String loggingProperties = "logging.properties";
}
