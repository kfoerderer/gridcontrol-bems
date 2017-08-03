package de.fzi.osh.core.configuration;

/**
 * Service for reading and writing configuration data
 * 
 * @author K. Foerderer
 *
 */
public interface ConfigurationService {

	/**
	 * Get a parameter as string
	 * 
	 * @param name
	 * @return
	 */
	public String get(String name);
	
	/**
	 * Read a data structure stored within the configuration
	 * 
	 * @param clazz
	 * @return
	 */
	public <T> T get(Class<T> clazz);
	
	/**
	 * Set a string parameter
	 * 
	 * @param name
	 * @param value
	 */
	public void set(String name, String value);
	
	/**
	 * Set the content of a data structure in the configuration
	 * 
	 * @param value
	 */
	public <T> void set(T value,  Class<T> clazz);
	
	/**
	 * Add a configuration listener to track changes to the configuration
	 * 
	 * @param configurationListener
	 */
	public void addConfigurationListener(ConfigurationListener configurationListener);
	
	/**
	 * Remove a configuration listener
	 * @param configurationListener
	 */
	public void removeConfigurationListener(ConfigurationListener configurationListener);
}
