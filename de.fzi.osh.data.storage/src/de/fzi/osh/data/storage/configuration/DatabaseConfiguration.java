package de.fzi.osh.data.storage.configuration;

/**
 * Configuration for a database connection
 * 
 * @author K. Foerderer
 *
 */
public class DatabaseConfiguration {
	public String url;
	public String user;
	public String password;
	public int maxConnections = 3;
}
