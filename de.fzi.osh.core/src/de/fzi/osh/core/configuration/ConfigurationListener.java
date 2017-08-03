package de.fzi.osh.core.configuration;

/**
 * Interface for reacting to configuration changes
 * 
 * @author K. Foerderer
 *
 */
public interface ConfigurationListener {

	/**
	 * Configuration has changed
	 * 
	 * @param name
	 * @param value
	 */
	public void changed(String name, String value);
	
	/**
	 * Configuration has changes
	 * 
	 * @param name
	 * @param value
	 * @param clazz
	 */
	public <T> void changed(String name, T value, Class<T> clazz);
}
