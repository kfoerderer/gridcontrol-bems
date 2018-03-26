package de.fzi.osh.forecasting.demand.implementation;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.data.logging.types.MeterData;
import de.fzi.osh.data.storage.timeseries.TimeSeries;
import de.fzi.osh.data.storage.timeseries.TimeSeriesStorageService;
import de.fzi.osh.forecasting.Forecast;
import de.fzi.osh.forecasting.ForecastingService;
import de.fzi.osh.forecasting.configuration.ElectricityDemandForecastingConfiguration;
import de.fzi.osh.forecasting.configuration.ElectricityDemandForecastingConfiguration.ForecastingMethod;
import de.fzi.osh.forecasting.demand.ElectricityDemandForecast;
import de.fzi.osh.time.TimeService;

/**
 * Service for doing electricity demand forecasts
 * 
 * Using slp h21 (E.DIS AG) OR averages of recent days (see configuration)
 * 
 * @author K. Foerderer
 *
 */
@Component(enabled=true, service=ForecastingService.class)
public class ElectricityDemandForecastingService implements ForecastingService {

	private static Logger log = Logger.getLogger(ElectricityDemandForecastingService.class.getName());
		
	private static TimeSeriesStorageService timeSeriesStorageService;
	private static ConfigurationService configurationService;	
	private static ElectricityDemandForecastingConfiguration configuration;
	private static TimeService timeService;
	
	private StandardLoadProfile profile;

	public ElectricityDemandForecastingService() {
	}
	
	@Override
	public Class<?>[] getCapabilities() {
		return new Class[] {ElectricityDemandForecast.class};
	}
	
	@Override
	public boolean canForecast(Class<? extends Forecast> forecast) {
		return forecast.equals(ElectricityDemandForecast.class);
	}	

	@Override
	public <T extends Forecast> T getForecast(ZonedDateTime from, ZonedDateTime to, Class<T> clazz, Object arguments) { 
		
		try {
		if(clazz == ElectricityDemandForecast.class) {			
			ElectricityDemandForecast demand = new ElectricityDemandForecast();
			
			demand.timestamp = new Timestamp(timeService.nowAsInstant().toEpochMilli());
			demand.forecastBegin = new Timestamp(from.toInstant().toEpochMilli());
			demand.forecastEnd = new Timestamp(to.toInstant().toEpochMilli());
			demand.timeSlotLength = configuration.intervalLength;
			
			// fill with zeroes if no forecast is wished
			if(configuration.forecastingMethod == ForecastingMethod.None) {
				
				// copy $from
				ZonedDateTime iterator = from.plusMinutes(0);
				
				do {
					// get value
					demand.addWattage(0);
					
					// move to next interval
					iterator = iterator.plusSeconds(configuration.intervalLength);
				} while(iterator.isBefore(to));
			
			} 
			// standard forecast
			else {
			
				// forecast via SLP?
				boolean getSlpForecast = configuration.forecastingMethod == ForecastingMethod.StandardLoadProfile;
				
				if(Duration.between(from, to).toDays() >= 1 && (to.getHour() != 0 || from.getHour() != 0) && getSlpForecast == false) {
					log.warning("Can not forecast across multiple days. Fallback to SLP.");
					getSlpForecast = true;
				}
				
				// try historic forecast if wished
				try {
					if(configuration.forecastingMethod == ForecastingMethod.HistoricData) {
						// get calendar to determine day
						DayOfWeek dayOfWeek = from.getDayOfWeek();
						
						NavigableMap<Integer, Integer> powers = new TreeMap<Integer,Integer>();
						// prepare map
						long startSecond = from.truncatedTo(ChronoUnit.DAYS).toEpochSecond();
				    	long currentSecond = from.toEpochSecond();    
				    	while(currentSecond < to.toEpochSecond()) {
				    		int secondOfDay = (int)(currentSecond - startSecond);
				    		powers.put(secondOfDay, 0);          		
				    		currentSecond += configuration.intervalLength;
				    	}
						
						// initialize iterator with begin of target day
						ZonedDateTime dayInterator = from.truncatedTo(ChronoUnit.DAYS); 
						int nDays = 0;
						for(int i = 0; i < configuration.numberOfDays; i++) {
							// begin and end times of a day for time series retrieval
							ZonedDateTime end;
							ZonedDateTime begin;
							
							if(dayOfWeek == DayOfWeek.SATURDAY) {
								// move begin back to previous Saturday
								begin = dayInterator.minusDays(7);
								end = begin.plusDays(1);							
							} else if(dayOfWeek == DayOfWeek.SUNDAY) {
								// move begin back to previous Sunday
								begin = dayInterator.minusDays(7);
								end = begin.plusDays(1);									
							} else {
								if(dayInterator.getDayOfWeek() == DayOfWeek.MONDAY) {
									// move begin back to previous Friday
									begin = dayInterator.minusDays(3);
									end = begin.plusDays(1);										
								} else {
									// move begin back to previous week day
									begin = dayInterator.minusDays(1);
									end = dayInterator;										
								}
							}
							dayInterator = begin;				
							
							NavigableMap<Integer, Integer> series = getSeries(begin, end);
							
							if(powers.size() == 0) {
					    		log.warning("Empty result set for historical meter data. Ignoring day.");
					    		continue;
					    	}
					    	
							// aggregate
							final int numberOfDays = nDays;
							powers.replaceAll((k,v) -> {
								Integer key = series.floorKey(k);
								if(key == null) {
									// if data is missing use average of previous days
									if(numberOfDays == 0) {
										return 0;
									} else {
										return v + v / numberOfDays;
									}
								} else {
									return v + series.get(key);
								}
							}); 
							
							// increment number of days
							nDays++;
						}
						
						// check forecast
				    	if(powers.entrySet().stream().filter(e -> e.getValue() > 0).count() == 0) {
				    		// only zeroes in map
				    		log.warning("Forecast only consists of 0s. Using SLP now.");
				    		getSlpForecast = true;
				    	} else {
					    	// compile result
					    	powers.forEach((k,v) -> demand.addWattage(v));
				    	}			    	
					}
				} catch(Exception e) {
					// reset wattages
					demand.wattages = "";
					log.severe(e.toString());
					getSlpForecast = true;
				}
				
				// if setting says so or forecast on historic data fails, do slp based forecast
				if(getSlpForecast) {
						
					// copy $from
					ZonedDateTime iterator = from.plusMinutes(0);
					
					do {
						// get value
						demand.addWattage((int)Math.round(configuration.multiplier * 
								profile.getWattageForTimestamp(new Timestamp(iterator.toInstant().toEpochMilli()))));
						
						// move to next interval
						iterator = iterator.plusSeconds(configuration.intervalLength);
					} while(iterator.isBefore(to));
				}				
			}
			
			return clazz.cast(demand);	
		} // if
		} catch(Exception e) {
			log.severe("Forecast generation failed: " + e.getMessage());
		}
		
		return null;
	}

	
	public NavigableMap<Integer, Integer> getSeries(ZonedDateTime begin, ZonedDateTime end)
	{
		log.finest("Retrieving consumption from " + begin + " to " + end + ".");
		// mapping for data association
    	// second of day -> power
    	NavigableMap<Integer, Integer> powers = new TreeMap<Integer, Integer>();
    	long startSecond = begin.toEpochSecond();
    	
    	String table = configuration.sourceUUID + "_" + MeterData.class.getAnnotation(TimeSeries.class).name();
    	
    	//retrieve historic data from time series store
    	List<MeterData> result;
		try {
			result = timeSeriesStorageService.select("time < " + end.toEpochSecond() + "s" + " AND time >= " + begin.toEpochSecond() + "s", 
					table,
					MeterData.class,
					configuration.intervalLength + "s", "MEAN");
		} catch (Exception e) {
			log.severe("Retrieving data from database failed.");
			log.severe(e.toString());
			return powers;
		}
    	
    	for (MeterData meterData : result) {
    		int secondOfDay = (int)(meterData.time.getEpochSecond() - startSecond);
    		powers.put(secondOfDay, (int)(meterData.totalActivePower * configuration.meterScalar));
    		// put a zero to make missing data 0 
    		powers.put(secondOfDay + configuration.intervalLength, 0);
		}            	          	            	
    	return powers;
	}
	
	public static ElectricityDemandForecastingConfiguration getConfiguration() {
		return configuration;
	}
	
	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		ElectricityDemandForecastingService.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		ElectricityDemandForecastingService.configurationService = null;
	}
	
	@Reference(
			name = "TimeService",
			service = TimeService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeService"
		)	
	protected synchronized void bindTimeService(TimeService timeService) {
		ElectricityDemandForecastingService.timeService = timeService;
	}
	protected synchronized void unbindTimeService(TimeService timeService) {
		ElectricityDemandForecastingService.timeService = null;
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
		configuration = configurationService.get(ElectricityDemandForecastingConfiguration.class);
		
		profile = new StandardLoadProfile();		
		profile.load(configuration.standardLoadProfile);
		
		// DEBUG:
		getForecast(ZonedDateTime.parse("2017-06-08T00:00:00+02:00"), ZonedDateTime.parse("2017-06-09T00:00:00+02:00"), ElectricityDemandForecast.class, null);
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
	}
}
