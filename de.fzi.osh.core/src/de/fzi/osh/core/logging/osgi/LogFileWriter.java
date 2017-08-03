package de.fzi.osh.core.logging.osgi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;

/**
 * LogListener for logging to JUL
 * 
 * @author K. Foerderer
 *
 */
public class LogFileWriter implements LogListener
{	
	private String filename = null;
	
	public LogFileWriter() {
		filename = "logs/" + Instant.now().toString().replace(":", "") + "_osgi.log";
	}
	
    public void logged(LogEntry log) {
    	try {
    		if(Files.notExists(Paths.get(filename))) {
    			Files.createFile(Paths.get(filename));
    		}
    	    Files.write(Paths.get(filename), (log.getMessage() + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND);
    	}catch (IOException e) {
    		e.printStackTrace();
    	}
    }

}