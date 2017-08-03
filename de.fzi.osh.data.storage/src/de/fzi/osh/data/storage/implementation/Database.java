package de.fzi.osh.data.storage.implementation;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.data.storage.Column;
import de.fzi.osh.data.storage.StorableDataObject;
import de.fzi.osh.data.storage.DataStorageService;
import de.fzi.osh.data.storage.Table;
import de.fzi.osh.data.storage.configuration.DatabaseConfiguration;

/**
 * Database service implementation
 * 
 * @author K. Foerderer
 *
 */
@Component(enabled=true, immediate=true, service=DataStorageService.class)
public class Database implements DataStorageService{

	private static Logger log = Logger.getLogger(Database.class.getName());
	
	private static ConfigurationService configurationService;
	
	private DatabaseConfiguration configuration;
	// Mapping Connection -> Boolean: Connection -> is in use?
	private Map<Connection, Boolean> connections;	
	
	@Activate
	protected synchronized void activate() throws Exception {
		Class.forName("com.mysql.jdbc.Driver");		
		
		configuration = configurationService.get(DatabaseConfiguration.class);
		
		connections = new HashMap<Connection, Boolean>();		
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
		for(Connection connection : connections.keySet()) {
			if(!connection.isClosed()) {
				connection.close();
			}
		}
	}
	
	@Override
	public Connection getConnection() throws Exception {
		
		synchronized(connections) {
			// search for an open connection
			for(Iterator<Map.Entry<Connection, Boolean>> iterator = connections.entrySet().iterator(); iterator.hasNext(); ) {
				Map.Entry<Connection, Boolean> entry = iterator.next();
				// is this connection in use?
				if(entry.getValue() == false) {
					// it is not in use, but first check if connection is still up
					if(entry.getKey().isClosed()) {
						// connection has been closed.
						iterator.remove();
					} else {
						// not in use -> mark as used and return
						entry.setValue(true);
						return entry.getKey();
					}
				}
			}
			
			// no open connection found
			
			// how many connections are open right now?
			if(connections.keySet().size() < configuration.maxConnections) {
				// maximum not reached yet -> open a new connection
				log.info("Connecting to database " + configuration.url + " with user " + configuration.user);
				Connection connection = DriverManager.getConnection(configuration.url, configuration.user, configuration.password);
				connections.put(connection, true);
				return connection;
			}
			
			// there is no connection available
			log.info("Maximum of " + configuration.maxConnections + " database connections reached");
			// wait until one is released
			connections.wait();
		}
		return getConnection();
	}
	
	@Override
	public void releaseConnection(Connection connection) {
		if(null != connection) { 
			try {
				synchronized(connections) {
					connections.put(connection, false);
					connections.notify();
				}
			} catch(Exception e) {
				log.severe(e.toString());
			}
		}
	}

	@Override
	public <T extends StorableDataObject> void createTable(Class<T> clazz) throws Exception {
		// get the @table annotation
		Table table = clazz.getAnnotation(Table.class);
		if(null == table) {
			throw new Exception("Missing @Table annotation.");
		}
		// create table
		createTable(table.name(), clazz);
	}
	
	@Override
	public <T extends StorableDataObject> void createTable(String table, Class<T> clazz) throws Exception {
		
		// check if the class is annotated with @table
		if(!clazz.isAnnotationPresent(Table.class)) {
			throw new Exception("Missing annotation '@Table'.");
		}
					
		// get a database connection
		Connection connection = getConnection();
		
		Statement statement = null;
		try {
		
			// build the query and add id as primary key
			String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ( id INT NOT NULL AUTO_INCREMENT,";
			
			// get all member variables
			Field[] fields = clazz.getDeclaredFields();
			int columns = 0;
			for(Field field : fields) {				
				// is it a table column?
				if(field.isAnnotationPresent(Column.class)) {
					Column column = field.getAnnotation(Column.class);
										
					// add column to the statement
					sql += " " + column.name() + " " + column.declaration() + ",";
					
					columns++;
				}
			}
			
			if(columns == 0) {
				throw new Exception("No columns found for table " + table + ".");
			}
			
			// declare primary keys and terminate query
			sql += " PRIMARY KEY( id ) );";
						
			statement = connection.createStatement();
			statement.executeUpdate(sql);
			
			log.info("Database '" + table + "' has been created.");
		} catch(Exception e) {
			throw e;
		} finally {
			releaseConnection(connection);
			if(null != statement) {
				statement.close();
			}			
		}
		
	}

	@Override
	public <T extends StorableDataObject> int insert(T obj, Class<T> clazz) throws Exception {
		// get the @table annotation
		Table table = clazz.getAnnotation(Table.class);
		if(null == table) {
			throw new Exception("Missing @Table annotation.");
		}
		// do insert
		return insert(obj, table.name(), clazz);
	}
	
	@Override
	public <T extends StorableDataObject> int insert(T obj, String table, Class<T> clazz) throws Exception {
		
		// check if the class is annotated with @table
		if(!clazz.isAnnotationPresent(Table.class)) {
			throw new Exception("Missing annotation '@Table'");
		}				
		
		// get a database connection
		Connection connection = getConnection();
		
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		try {
			// build SQL query
			String sql = "INSERT INTO `" + table + "` ( ";			
			
			// get all member variables
			Field[] fields = clazz.getDeclaredFields();
			
			String values = "";
			for(Field field : fields) {				
				// is it a table column?
				if(field.isAnnotationPresent(Column.class)) {
					Column column = field.getAnnotation(Column.class);
			
					if(!column.auto_creation()) {
						// add column to the statement
						sql += (values.length() == 0 ? "" : ", ") + column.name();
						values += (values.length() == 0 ? "" : ", ") +  "?";	
					}
				}
			}
			sql += " ) VALUES ( " + values + " );";
			
			statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
			// set values
			int index = 1;
			boolean access;
			for(Field field : fields) {				
				// is it a table column?
				if(field.isAnnotationPresent(Column.class)) {
					Column column = field.getAnnotation(Column.class);
			
					if(!column.auto_creation()) {
						// set value
						
						// make field accessible if private
						access = field.isAccessible();
						if(!access) {
							field.setAccessible(true);
						}
						
						// get object value
						statement.setObject(index, field.get(obj));
						index++;
						
						// restore access rights
						if(!access) {
							field.setAccessible(access);
						}
					}
				}
			}
			
			// do query
			statement.executeUpdate();
			// get id
			resultSet = statement.getGeneratedKeys();
			if(!resultSet.next()) {
				throw new Exception("Retrieving generated keys failed for table " + table + ".");
			}
			
			// update id on object
			obj.setId(resultSet.getInt(1));
			return obj.getId();			
		} catch(Exception e) {
			throw e;
		} finally {
			releaseConnection(connection);
			if(null != statement) {
				statement.close();
			}
			if(null != resultSet) {
				resultSet.close();
			}			
		}
	}
	

	@Override
	public <T extends StorableDataObject> List<T> select(String where, Class<T> clazz) throws Exception {
		// get the @table annotation
		Table table = clazz.getAnnotation(Table.class);
		if(null == table) {
			throw new Exception("Missing @Table annotation.");
		}
		// do select
		return select(where, table.name(), clazz);
	}

	@Override
	public <T extends StorableDataObject> List<T> select(String where, String table, Class<T> clazz) throws Exception {
		
		// check if the class is annotated with @table
		if(!clazz.isAnnotationPresent(Table.class)) {
			throw new Exception("Missing annotation '@Table'");
		}
		
		// get a database connection
		Connection connection = getConnection();
		

		Statement statement = null;
		ResultSet resultSet = null;
		try {
			
			// build SQL query
			String sql = "SELECT * FROM `" + table + "`";
			
			// select everything?
			if(where.length() > 0) {
				sql += " WHERE " + where + ";";
			} else {
				sql += ";";
			}
			
			statement = connection.createStatement();
			log.finest("Executing sql query: " + sql);
			resultSet = statement.executeQuery(sql);

			// get all member variables
			Field[] fields = clazz.getDeclaredFields();
			
			List<T> objects = new ArrayList<T>();
			boolean access;
			while(resultSet.next()) {
				T obj = clazz.newInstance();
				
				// set id, since it is not an annotated field
				obj.setId(resultSet.getInt("id"));
				
				for(Field field : fields) {				
					// is it a table column?
					if(field.isAnnotationPresent(Column.class)) {
						Column column = field.getAnnotation(Column.class);
				
						// make field accessible if private
						access = field.isAccessible();
						if(!access) {
							field.setAccessible(true);
						}
						
						// casting: may need some more work in the future
						if(field.getType() == byte.class) {
							field.set(obj, resultSet.getByte(column.name()));
						} else if(field.getType() == short.class) {
							field.set(obj, resultSet.getShort(column.name()));
						} else if(field.getType() == float.class) {
							field.set(obj, resultSet.getFloat(column.name()));
						} else {
							// set value
							field.set(obj, resultSet.getObject(column.name()));	
						}
						
						
						// restore access rights
						if(!access) {
							field.setAccessible(access);
						}
					}
				}
				
				objects.add(obj);
			}
			
			return objects;
			
		} catch(Exception e) {
			throw e;
		} finally {
			releaseConnection(connection);
			if(null != statement) {
				statement.close();
			}
			if(null != resultSet) {
				resultSet.close();
			}	
		}
	}
	
	@Override
	public <T extends StorableDataObject> void update(T obj, Class<T> clazz) throws Exception {
		// get the @table annotation
		Table table = clazz.getAnnotation(Table.class);
		if(null == table) {
			throw new Exception("Missing @Table annotation.");
		}
		// do update
		update(obj, table.name(), clazz);
	}
	
	@Override
	public <T extends StorableDataObject> void update(T obj, String table, Class<T> clazz) throws Exception {
		
		// check if the class is annotated with @table
		if(!clazz.isAnnotationPresent(Table.class)) {
			throw new Exception("Missing annotation '@Table'");
		}
		
		// get a database connection
		Connection connection = getConnection();
		
		PreparedStatement statement = null;
		try {
			// build SQL query
			String sql = "UPDATE `" + table + "` SET ";			
			
			// get all member variables
			Field[] fields = clazz.getDeclaredFields();
			
			int index = 0;
			for(Field field : fields) {				
				// is it a table column?
				if(field.isAnnotationPresent(Column.class)) {
					Column column = field.getAnnotation(Column.class);
			
					if(!column.auto_update()) {
						// add column to the statement
						sql += (index == 0 ? "" : ", ") + column.name() + " = ?";
						index++;
					}
				}
			}
			sql += " WHERE id = " + obj.getId() + ";";
			
			statement = connection.prepareStatement(sql);
			// set values
			index = 1;
			boolean access;
			for(Field field : fields) {				
				// is it a table column?
				if(field.isAnnotationPresent(Column.class)) {
					Column column = field.getAnnotation(Column.class);
			
					if(!column.auto_update()) {
						// set value
						
						// make field accessible if private
						access = field.isAccessible();
						if(!access) {
							field.setAccessible(true);
						}
						
						// get object value
						statement.setObject(index, field.get(obj));
						index++;
						
						// restore access rights
						if(!access) {
							field.setAccessible(access);
						}
					}
				}
			}
			
			// do query
			statement.executeUpdate();
			
		} catch(Exception e) {
			throw e;
		} finally {
			releaseConnection(connection);
			if(null != statement) {
				statement.close();
			}	
		}		
	}

	@Override
	public <T extends StorableDataObject> void delete(int id, Class<T> clazz) throws Exception {
		// get the @table annotation
		Table table = clazz.getAnnotation(Table.class);
		if(null == table) {
			throw new Exception("Missing @Table annotation.");
		}
		// do delete
		delete(id, table.name(), clazz);
	}
	
	@Override
	public <T extends StorableDataObject> void delete(int id, String table, Class<T> clazz) throws Exception {
		// check if the class is annotated with @table
		if(!clazz.isAnnotationPresent(Table.class)) {
			throw new Exception("Missing annotation '@Table'");
		}
		
		// get a database connection
		Connection connection = getConnection();
		
		Statement statement = null;
		try {
			// build SQL query
			String sql = "DELETE FROM `" + table + "` WHERE id="+ id + ";";
			
			// do query
			statement = connection.createStatement();
			statement.executeUpdate(sql);
			
		} catch(Exception e) {
			throw e;
		} finally {
			releaseConnection(connection);
			if(null != statement) {
				statement.close();
			}
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
}
