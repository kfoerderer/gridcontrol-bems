package de.fzi.osh.forecasting.solar.implementation;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.data.logging.types.MeterData;
import de.fzi.osh.data.storage.timeseries.TimeSeriesStorageService;
import de.fzi.osh.data.storage.timeseries.TimeSeries;
import de.fzi.osh.forecasting.Forecast;
import de.fzi.osh.forecasting.ForecastingService;
import de.fzi.osh.forecasting.configuration.SolarPowerForecastingConfiguration;
import de.fzi.osh.forecasting.solar.SolarPowerForecast;
import de.fzi.osh.time.TimeService;

/**
 * Solar power forecasting service
 * 
 * forecast is based on power 24 hours ago
 * 
 * @author K. Foerderer
 *
 */
@Component(enabled=true, service=ForecastingService.class)
public class SolarPowerForecastingService implements ForecastingService{

	private static Logger log = Logger.getLogger(SolarPowerForecastingService.class.getName());
	
	public SolarPowerForecastingService() {
	}
		
	private static TimeSeriesStorageService timeSeriesStorageService;
	private static ConfigurationService configurationService;	
	private static SolarPowerForecastingConfiguration configuration;	
	private static TimeService timeService;
	
	public static SolarPowerForecastingConfiguration getConfiguration() {
		return configuration;
	}
	
	@Override
	public Class<?>[] getCapabilities() {
		return new Class[] {SolarPowerForecast.class};
	}
	
	@Override
	public boolean canForecast(Class<? extends Forecast> forecast) {
		return forecast.equals(SolarPowerForecast.class);
	}

	@Override
	public <T extends Forecast> T getForecast(ZonedDateTime from, ZonedDateTime to, Class<T> clazz, Object argument) {		
		
		try {
			if(clazz == SolarPowerForecast.class) {
				// check if a data source has been passed.
				if(null == argument || !(argument instanceof UUID)) {
					log.severe("No uuid has been passed as argument.");
					return null;
				}
				
				UUID uuid = (UUID) argument;
				
				SolarPowerForecast pv = new SolarPowerForecast();
				
				pv.timestamp = new Timestamp(timeService.nowAsInstant().toEpochMilli());
				pv.forecastBegin = new Timestamp(from.toInstant().toEpochMilli());
				pv.forecastEnd = new Timestamp(to.toInstant().toEpochMilli());
				pv.timeSlotLength = configuration.intervalLength;
				
				// do database request
				ZonedDateTime yesterdayEnd = timeService.nowAsZonedDateTime().truncatedTo(ChronoUnit.DAYS);
				ZonedDateTime yesterdayBegin = yesterdayEnd.minusDays(1);
				String table = uuid + "_" + MeterData.class.getAnnotation(TimeSeries.class).name();            
            	
            	// mapping for data association
            	// second of day -> power
            	NavigableMap<Integer, Integer> powers = new TreeMap<Integer, Integer>();
            	long startOfDay = yesterdayBegin.toEpochSecond();
            	
            	
            	//retrieve historic data from time series store
            	List<MeterData> result = timeSeriesStorageService.select("time < " + yesterdayEnd.toEpochSecond() + "s" + " AND time >= " + yesterdayBegin.toEpochSecond() + "s", 
						table,
						MeterData.class,
						configuration.intervalLength + "s", "MEAN");
            	
            	for (MeterData meterData : result) {
            		int secondOfDay = (int)(meterData.time.getEpochSecond() - startOfDay);
            		powers.put(secondOfDay, (int)(meterData.totalActivePower * configuration.scalar));
            		// put a zero to make missing data 0 
            		powers.put(secondOfDay + configuration.intervalLength, 0);
				}            	            	            	
            	
            	if(powers.size() == 0) {
            		log.warning("Empty result set for historical meter data. Assuming 0.");
            	}
            	
            	// now use the mapping to create a forecast
            	startOfDay = from.truncatedTo(ChronoUnit.DAYS).toEpochSecond();
            	long currentSecond = from.toEpochSecond();    
            	int missingData = 0;
            	while(currentSecond < to.toEpochSecond()) {
            		int secondOfDay = (int)(currentSecond - startOfDay);
            		
            		// if the forecast period extends to the following day
            		if(secondOfDay >= 25 * 60 * 60) { // daylight savings [!]
            			// 25 since at night pv equals 0, so no need to take be get the exact second of day
            			startOfDay = Instant.ofEpochSecond(startOfDay).atZone(ZoneId.systemDefault()).plusDays(1).toEpochSecond();
            			secondOfDay = (int)(currentSecond - startOfDay);
            		}
            		
            		Integer key = powers.floorKey(secondOfDay);
            		if(powers.size() == 0) {
            			pv.addWattage(0);
            		} else if(key == null) {
            			missingData++;
            			pv.addWattage(0);
            		} else {
            			pv.addWattage(powers.get(key));
            		}            		
            		currentSecond += configuration.intervalLength;
            	}
            	
            	if(missingData > 0) {
        			log.warning("No floor value found for " + missingData + " values. Added 0s to forecast.");
            	}
            	
				return clazz.cast(pv);	
			}
		} catch(Exception e) {
			log.severe("Forecast generation failed.");
			log.severe(e.toString());
		} 		
		
		return null;
	}
	
	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		SolarPowerForecastingService.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		SolarPowerForecastingService.configurationService = null;
	}
	
	@Reference(
			name = "TimeService",
			service = TimeService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeService"
		)	
	protected synchronized void bindTimeService(TimeService timeService) {
		SolarPowerForecastingService.timeService = timeService;
	}
	protected synchronized void unbindTimeService(TimeService timeService) {
		SolarPowerForecastingService.timeService = null;
	}
	
	@Reference(
			name = "TimeSeriesStorageService",
			service = TimeSeriesStorageService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeSeriesStorageService"
		)	
	protected synchronized void bindTimeSeriesStorageService(TimeSeriesStorageService dataStorageService) {
		timeSeriesStorageService = dataStorageService;
	}
	protected synchronized void unbindTimeSeriesStorageService(TimeSeriesStorageService dataStorageService) {
		timeSeriesStorageService = null;
	}

	@Activate
	protected synchronized void activate() throws Exception {
		configuration = configurationService.get(SolarPowerForecastingConfiguration.class);
		
		/*
		ZonedDateTime now = ZonedDateTime.now();
		ZonedDateTime tomorrow = ZonedDateTime.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth(), 0, 0, 0, 0, ZoneId.systemDefault()).plusDays(1);
		getForecast(tomorrow, tomorrow.plusDays(1), SolarPowerForecast.class);*/
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
	}	
}
