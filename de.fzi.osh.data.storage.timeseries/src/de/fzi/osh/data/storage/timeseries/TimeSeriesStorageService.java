package de.fzi.osh.data.storage.timeseries;

import java.util.List;

//TODO: Switch to timeseries types after transition to influxDB
import de.fzi.osh.data.storage.timeseries.StorableTimeSeriesObservation;

/**
 * Interface for database queries
 * 
 * @author K. Foerderer
 *
 */
public interface TimeSeriesStorageService {

	/**
	 * Get a database connection from the pool. If no connection is available, the function blocks until one is released.
	 * 
	 * @return
	 */
	//public Connection getConnection() throws Exception;
	
	/**
	 * Release a connection into the connection pool.
	 * 
	 * @param connection
	 * @throws Exception
	 */
	//public void releaseConnection(Connection connection);
		
	/**
	 * Insert an annotated object into the table defined by its annotation.
	 * 
	 * @param obj
	 */
	public<T extends StorableTimeSeriesObservation> void insert(T obj, Class<T> clazz) throws Exception;
	
	/**
	 * Insert an annotated object into a table.
	 * 
	 * @param obj
	 * @param table
	 */
	public<T extends StorableTimeSeriesObservation> void insert(T obj, String table, Class<T> clazz) throws Exception;
	
	/**
	 * Select an annotated object from the database.
	 * 
	 * @param sql
	 * @param clazz
	 * @return
	 */
	public<T extends StorableTimeSeriesObservation> List<T> select(String where, Class<T> clazz, String interval) throws Exception;
	
	/**
	 * Select an annotated object from the given table of the database.
	 * 
	 * @param sql
	 * @param table
	 * @param clazz
	 * @return
	 */
	public<T extends StorableTimeSeriesObservation> List<T> select(String where, String table, Class<T> clazz, String interval) throws Exception;
	
	
	public<T extends StorableTimeSeriesObservation> List<T> select(String where, String table, Class<T> clazz, String interval, String aggr) throws Exception;
	
	/**
	 * Update an annotated object in the annotation table based on the object id.
	 * 
	 * @param obj
	 * @return database id
	 */
	//public<T extends StorableTimeSeries> void update(T obj, Class<T> clazz) throws Exception;
	
	/**
	 * Update an annotated object in a given table based on the object id.
	 * 
	 * @param obj
	 * @param table
	 * @return database id
	 */
	//public<T extends StorableTimeSeries> void update(T obj, String table, Class<T> clazz) throws Exception;
	
	/**
	 * Delete the annotated object with id=$id.
	 * 
	 * @param id
	 * @param clazz
	 */
	//public<T extends StorableTimeSeries> void delete(int id, Class<T> clazz) throws Exception;
	
	/**
	 * Delete the annotated object with id=$id from the given table.
	 * 
	 * @param id
	 * @param table
	 * @param clazz
	 */
	//public<T extends StorableTimeSeries> void delete(int id, String table, Class<T> clazz) throws Exception;
}
