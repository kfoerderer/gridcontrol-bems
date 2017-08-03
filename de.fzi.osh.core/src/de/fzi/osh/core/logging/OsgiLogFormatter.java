package de.fzi.osh.core.logging;

import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import de.fzi.osh.core.TimeProvider;
import de.fzi.osh.time.TimeService;

public class OsgiLogFormatter extends Formatter{

	@Override
	public String format(LogRecord record) 
	{
		StringBuilder logEntry = new StringBuilder();
		
		TimeService timeService = TimeProvider.getService();
		if(null == timeService) {
			// this should not happen, but still, take care of it
			logEntry.append("<RT>" + Instant.now().toString());
		} else {
			logEntry.append(timeService.nowAsInstant().toString());
		}
		
		
		logEntry.append(" [" + record.getLevel().getName() + "] ");		
				
		logEntry.append(record.getMessage() + " @ " + record.getLoggerName());
		
		return logEntry.toString();
	}

	public String getHead(Handler handler) 
	{
		return 	"Format: UTC [Flags] Message @ Logger\r\n";
				
	}
	
	public String getTail(Handler handler)
	{
		return 	"";
	}
}
