package de.fzi.osh.wamp.device;

import java.util.UUID;

/**
 * Interface for device management
 * 
 * @author K. Foerderer
 *
 */
public class DeviceTopics {
	
	private String prefix;
	
	/**
	 * Constructor
	 * 
	 * Sets up all the topic names.
	 * 
	 * @param topicPrefix Topic topicPrefix, e.g. "fzi.osh".
	 */
	public DeviceTopics(String prefix) {
		this.prefix = prefix;
	}
	
	/**
	 * Topic for setting a driver state.
	 */
	public String setDriverState(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".driver.state.set";
	}
	
	/**
	 * Topic for retrieving a driver state.
	 */
	public String getDriverState(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".driver.state.get";
	}
}
