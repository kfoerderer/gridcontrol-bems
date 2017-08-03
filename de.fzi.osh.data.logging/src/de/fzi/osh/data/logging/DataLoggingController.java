package de.fzi.osh.data.logging;


import java.util.logging.Logger;

import de.fzi.osh.core.oc.Controller;
import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.data.logging.configuration.DataLoggerConfiguration;
import de.fzi.osh.data.logging.types.BatterySocData;
import de.fzi.osh.data.logging.types.MeterData;
import de.fzi.osh.data.storage.timeseries.TimeSeriesStorageService;
import de.fzi.osh.data.storage.timeseries.TimeSeries;

/**
 * Controller
 * 
 * Stores data in two databases:
 * 
 * 	uuid_TABLE 			for a permanent storage
 * 	uuid_TABLE_cache 	for caching 
 * 
 * 
 * @author K. Foerderer
 *
 */
public class DataLoggingController extends Controller<DataLogger, DataLoggerConfiguration> {

	private Logger log = Logger.getLogger(DataLoggingController.class.getName());
	
	public DataLoggingController(DataLogger component) {
		super(component);
	}

	@Override
	public void update(DataObject data) {
		if(data instanceof MeterData)
		{
			MeterData smartMeterData = (MeterData) data;						
			
			TimeSeriesStorageService timeSeriesStorage = DataLogger.getTimeSeriesStorageService();
			
			TimeSeries series = MeterData.class.getAnnotation(TimeSeries.class);
			try {				
				
				timeSeriesStorage.insert(smartMeterData, smartMeterData.uuid + "_" + series.name(), MeterData.class);								
										
			} catch (Exception e) {
				log.severe("Could not write smart meter data to database.");
				log.severe(e.toString());
			}
		}
		
		if(data instanceof BatterySocData) {
			BatterySocData socData = (BatterySocData) data;
			
			TimeSeriesStorageService timeSeriesStorage = DataLogger.getTimeSeriesStorageService();
						
			TimeSeries series = BatterySocData.class.getAnnotation(TimeSeries.class);
			try {				
				timeSeriesStorage.insert(socData, socData.uuid + "_" + series.name(), BatterySocData.class);
			} catch (Exception e) {
				log.severe("Could not write soc data to database.");
				log.severe(e.toString());
			}
		}
	}
}
