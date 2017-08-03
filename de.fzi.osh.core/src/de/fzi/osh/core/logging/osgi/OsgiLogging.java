package de.fzi.osh.core.logging.osgi;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;

/**
 * Component that registers log readers and holds a reference to the log service.
 * 
 * @author K. Foerderer
 *
 */
@Component
public class OsgiLogging {

	private static LogService logService;
	
	private LogFileWriter writer = new LogFileWriter();
	
	/**
	 * Returns a reference of the osgi log service.
	 * 
	 * @return
	 */
	public static synchronized LogService getLogService() {
		return logService;
	}
	
	@Reference(
			name = "LogReaderService",
			service = LogReaderService.class,
			cardinality = ReferenceCardinality.MULTIPLE,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindLogReader"
		)
	protected synchronized void bindLogReader(LogReaderService logReader)
	{
	    logReader.addLogListener(writer);
	}

	protected synchronized void unbindLogReader(LogReaderService logReader)
	{
	    logReader.removeLogListener(writer);
	}

	@Reference(
			name = "LogService",
			service = LogService.class,
			cardinality = ReferenceCardinality.MULTIPLE,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindLogService"
		)
	protected synchronized void bindLogService(LogService logService)
	{
		OsgiLogging.logService = logService;
	}

	protected synchronized void unbindLogService(LogService logService)
	{
		OsgiLogging.logService = null;
	}
}
