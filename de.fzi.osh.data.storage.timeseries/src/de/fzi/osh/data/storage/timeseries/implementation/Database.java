package de.fzi.osh.data.storage.timeseries.implementation;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.core.configuration.ConfigurationService;
//TODO: Switch to timeseries types after transition to influxDB
import de.fzi.osh.data.storage.timeseries.TimeSeriesStorageService;
import de.fzi.osh.data.storage.timeseries.StorableTimeSeriesObservation;
import de.fzi.osh.data.storage.timeseries.TimeSeries;
import de.fzi.osh.data.storage.timeseries.configuration.DatabaseConfiguration;

/**
 * Database service implementation
 * 
 * @author K. Foerderer
 *
 */
@Component(enabled=true, immediate=true, service=TimeSeriesStorageService.class)
public class Database implements TimeSeriesStorageService{

	private static Logger log = Logger.getLogger(Database.class.getName());
	
	private static ConfigurationService configurationService;
	
	private DatabaseConfiguration configuration;
	
	private InfluxDB influxConn;
	
	@Activate
	protected synchronized void activate() throws Exception {		
		
		configuration = configurationService.get(DatabaseConfiguration.class);						
		
		//TODO: check influxDB connection management
		//TODO: Check whether synchronization is required, looks ok so far!
		influxConn = InfluxDBFactory.connect(configuration.url,configuration.user,configuration.password);
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
		//TOOD: check influx connection handling
		influxConn.close();
	}
	

	@Override
	public <T extends StorableTimeSeriesObservation> void insert(T obj, Class<T> clazz) throws Exception {
		// get the @table annotation
		TimeSeries series = clazz.getAnnotation(TimeSeries.class);
		if(null == series) {
			throw new Exception("Missing @Table annotation.");
		}
		// do insert
		insert(obj, series.name(), clazz);
	}
	
	@Override
	public <T extends StorableTimeSeriesObservation> void insert(T obj, String table, Class<T> clazz) throws Exception {
		
		// check if the class is annotated with @series
		if(!clazz.isAnnotationPresent(TimeSeries.class)) {
			throw new Exception("Missing annotation '@Series'");
		}
				
		{

			BatchPoints batchPoints = BatchPoints
										.database(configuration.database)
										.retentionPolicy("autogen")
										.consistency(ConsistencyLevel.ALL)
										.build();
			
			Point.Builder builder = Point.measurement(table);
			
			builder.time(obj.time.toEpochMilli(), TimeUnit.MILLISECONDS);
			
			Field[] fields = clazz.getFields();			
			for (Field field: fields) {
				if (field.isAnnotationPresent(TimeSeries.Series.class)) {
					if(field.getType() == int.class) {						
						builder.addField(field.getName(), field.getInt(obj));
					} else if(field.getType() == long.class) {						
						builder.addField(field.getName(), field.getLong(obj));
					} else if(field.getType() == byte.class) {
						builder.addField(field.getName(), field.getByte(obj));
					} else {
						log.warning("Value Data Type "+ field.getType() + " is not supported");
					}					
				} else if (field.isAnnotationPresent(TimeSeries.Tag.class)) {					
					builder.tag(field.getName(), field.get(obj).toString());
				}						
			}
			
			Point point = builder.build();
			batchPoints.point(point);			
			influxConn.write(batchPoints);			
		}	
		
	}
	
	
	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		Database.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		configurationService = null;
	}
		
	
	@Override
	public <T extends StorableTimeSeriesObservation> List<T> select(String where, Class<T> clazz, String interval) 
		throws Exception {
			TimeSeries series = clazz.getAnnotation(TimeSeries.class);
			if(null == series) {
				throw new Exception("Missing @Series annotation.");
			}			
			// do select
			return select(where, series.name(), clazz, interval);
	}

	@Override
	public <T extends StorableTimeSeriesObservation> List<T> select(String where, String table, Class<T> clazz, String interval)
			throws Exception {
		
		// check if the class is annotated with @series
		if(!clazz.isAnnotationPresent(TimeSeries.class)) {
			throw new Exception("Missing annotation '@Series'");
		}
		
		// do select
		return select(where, table, clazz, interval, "FIRST");
	}

	@Override
	public <T extends StorableTimeSeriesObservation> List<T> select(String where, String table, Class<T> clazz, String interval,
			String aggr) throws Exception {
		// check if the class is annotated with @series
		if(!clazz.isAnnotationPresent(TimeSeries.class)) {
			throw new Exception("Missing annotation '@Series'");
		}
		
		// get all member variables
		Field[] fields = clazz.getDeclaredFields();
		List<Field> selFields = new ArrayList<Field>(fields.length);		
		
		//select all time series values for now
		for (Field field : fields) {
			if (field.isAnnotationPresent(TimeSeries.Series.class)) {
				selFields.add(field);				
			}
		}
		
		//TODO: check performance without StringBuffer usage
		String selection = "";
		for ( Iterator<Field> i = selFields.iterator(); i.hasNext(); ) {
			Field field = i.next();			
			selection += aggr + "(" + field.getName() + ")";
			if (i.hasNext()) {
				selection += ", ";
			}
		}
		
		
		String queryStat = "SELECT " + selection + " FROM \"" + table + "\" WHERE " + where + " GROUP BY time(" + interval + ")" + " fill(none)";
		
		Query query = new Query(queryStat,configuration.database);

		QueryResult queryResult = influxConn.query(query,TimeUnit.MILLISECONDS);
		
		List<Result> resultSet = queryResult.getResults();		
		//create result data structure
		List<T> result = new ArrayList<T>();
		for (Result item : resultSet) {						
			List<Series> series = item.getSeries();
			if(null != series) {
				for (Series elem : series) {
					List<List<Object>> values = elem.getValues();				
					for (List<Object> entry : values) {
						T resItem = clazz.newInstance();
						
						//TODO: check why there are always double values returned from Influx driver (timestamps and series)!
						
						//time is always first element
						Double o = (Double)entry.get(0);								
						resItem.time = Instant.ofEpochMilli(o.longValue());
						
						int i = 0;
						for (Field field : selFields) {
							i++;						
							Double d = (Double)entry.get(i);
							// casting: may need some more work in the future
							if(field.getType() == int.class) {
								field.set(resItem, d.intValue());
							} else if(field.getType() == long.class) {
								field.set(resItem, d.longValue());
							} else if(field.getType() == byte.class) {
								field.set(resItem, d.byteValue());
							} else {
								log.warning("Value Data Type "+ field.getType() + " is not supported");
							}													
						}
						
						result.add(resItem);
					}	
				}
			}					
		}		
		
		return result;
	}
	
	

}
