package de.fzi.osh.data.storage;

import java.sql.Connection;
import java.util.List;

/**
 * Interface for database queries
 * 
 * @author K. Foerderer
 *
 */
public interface DataStorageService {

	/**
	 * Get a database connection from the pool. If no connection is available, the function blocks until one is released.
	 * 
	 * @return
	 */
	public Connection getConnection() throws Exception;
	
	/**
	 * Release a connection into the connection pool.
	 * 
	 * @param connection
	 * @throws Exception
	 */
	public void releaseConnection(Connection connection);
	
	/**
	 * Create a table for an annotated type.
	 * 
	 * @param obj
	 */
	public<T extends StorableDataObject> void createTable(Class<T> clazz) throws Exception;
	
	/**
	 * Creates table with the given name for objects of type clazz.
	 * 
	 * @param name
	 * @param clazz
	 * @throws Exception
	 */
	public<T extends StorableDataObject> void createTable(String name, Class<T> clazz) throws Exception;
	
	/**
	 * Insert an annotated object into the table defined by its annotation.
	 * 
	 * @param obj
	 * @return database id
	 */
	public<T extends StorableDataObject> int insert(T obj, Class<T> clazz) throws Exception;
	
	/**
	 * Insert an annotated object into a table.
	 * 
	 * @param obj
	 * @param table
	 * @return database id
	 */
	public<T extends StorableDataObject> int insert(T obj, String table, Class<T> clazz) throws Exception;
	
	/**
	 * Select an annotated object from the database.
	 * 
	 * @param sql
	 * @param clazz
	 * @return
	 */
	public<T extends StorableDataObject> List<T> select(String where, Class<T> clazz) throws Exception;
	
	/**
	 * Select an annotated object from the given table of the database.
	 * 
	 * @param sql
	 * @param table
	 * @param clazz
	 * @return
	 */
	public<T extends StorableDataObject> List<T> select(String where, String table, Class<T> clazz) throws Exception;
	
	/**
	 * Update an annotated object in the annotation table based on the object id.
	 * 
	 * @param obj
	 * @return database id
	 */
	public<T extends StorableDataObject> void update(T obj, Class<T> clazz) throws Exception;
	
	/**
	 * Update an annotated object in a given table based on the object id.
	 * 
	 * @param obj
	 * @param table
	 * @return database id
	 */
	public<T extends StorableDataObject> void update(T obj, String table, Class<T> clazz) throws Exception;
	
	/**
	 * Delete the annotated object with id=$id.
	 * 
	 * @param id
	 * @param clazz
	 */
	public<T extends StorableDataObject> void delete(int id, Class<T> clazz) throws Exception;
	
	/**
	 * Delete the annotated object with id=$id from the given table.
	 * 
	 * @param id
	 * @param table
	 * @param clazz
	 */
	public<T extends StorableDataObject> void delete(int id, String table, Class<T> clazz) throws Exception;
}
