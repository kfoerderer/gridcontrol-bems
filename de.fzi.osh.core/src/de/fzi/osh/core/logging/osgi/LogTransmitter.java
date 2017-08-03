package de.fzi.osh.core.logging.osgi;

import java.util.logging.Logger;

import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogService;

/**
 * LogListener for logging to JUL
 * 
 * @author K. Foerderer
 *
 */
@Deprecated
public class LogTransmitter implements LogListener
{	
	public void logged(LogEntry log) {
        if (log.getMessage() != null) {
        	switch(log.getLevel()) {
        	case LogService.LOG_DEBUG:
            	Logger.getLogger(log.getBundle().getSymbolicName()).info("[OSGI:DEBUG] " + log.getMessage());
        		break;
        	case LogService.LOG_INFO:
        		Logger.getLogger(log.getBundle().getSymbolicName()).info("[OSGI] " + log.getMessage());
        		break;
        	case LogService.LOG_WARNING:
            	Logger.getLogger(log.getBundle().getSymbolicName()).warning("[OSGI] " + log.getMessage());
        		break;
        	case LogService.LOG_ERROR:
            	Logger.getLogger(log.getBundle().getSymbolicName()).severe("[OSGI] " + log.getMessage());
        		break;
        	default:
            	Logger.getLogger(log.getBundle().getSymbolicName()).fine("Log level unknown for:");
            	Logger.getLogger(log.getBundle().getSymbolicName()).fine("[OSGI] " + log.getMessage());
        	}        	
        }     
    }
}