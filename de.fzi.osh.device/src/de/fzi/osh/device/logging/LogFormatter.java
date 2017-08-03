package de.fzi.osh.device.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter{

	@Override
	public String format(LogRecord record) 
	{
		StringBuilder logEntry = new StringBuilder();
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		logEntry.append(dateFormat.format(new Date(record.getMillis())));
		
		logEntry.append(" -- " + record.getLevel().getName() + " -- " + record.getLoggerName());		
				
		logEntry.append(" -- " + record.getMessage());
		
		logEntry.append("\r\n");
		
		return logEntry.toString();
	}

	public String getHead(Handler handler) 
	{
		return 	"Format: Time -- Level -- Logger -- Message\r\n";
				
	}
	
	public String getTail(Handler handler)
	{
		return 	"";
	}
}
