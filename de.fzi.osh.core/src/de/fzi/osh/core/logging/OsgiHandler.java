package de.fzi.osh.core.logging;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.osgi.service.log.LogService;

import de.fzi.osh.core.logging.osgi.OsgiLogging;

/**
 * JUL Handler forwarding logging messages to osgi.
 * 
 * @author K. Foerderer
 *
 */
public class OsgiHandler extends Handler{

	@Override
	public void close() throws SecurityException {
		// nothing to do
	}

	@Override
	public void flush() {
		// nothing to do
	}

	@Override
	public void publish(LogRecord record) {
		// forward to Osgi log service
		LogService log = OsgiLogging.getLogService();
		String message = getFormatter().format(record);
		if(null == log) {
			System.out.println("No OSGI log service found.");
			System.out.println(message);
		} else {
			int osgiLevel = LogService.LOG_ERROR;
			Level level = record.getLevel();
			if(level == Level.SEVERE) { // highest value
				osgiLevel = LogService.LOG_ERROR;
			} else if(level == Level.WARNING) {
				osgiLevel = LogService.LOG_WARNING;
			} else if(level == Level.INFO || level == Level.CONFIG) {
				osgiLevel = LogService.LOG_INFO;
			} else if(level.intValue() <= Level.FINE.intValue()) {
				osgiLevel = LogService.LOG_DEBUG;
			}
			log.log(osgiLevel, message);
		}
	}
	
}
