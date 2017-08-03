package de.fzi.osh.data.storage.timeseries.configuration;

/**
 * Configuration for a database connection
 * 
 * @author K. Foerderer
 *
 */
public class DatabaseConfiguration {
	/**
	 * URL of influxdb server.
	 */
	public String url;
	/***
	 * User name for authentication.
	 */
	public String user;
	/**
	 * Password for authentication.
	 */
	public String password;
	/**
	 * Name of database.
	 */
	public String database;
}
