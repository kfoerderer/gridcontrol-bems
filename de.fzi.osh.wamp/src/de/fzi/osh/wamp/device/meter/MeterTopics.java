package de.fzi.osh.wamp.device.meter;

import java.util.UUID;

/**
 * Topics for metering devices.
 * 
 * @author K. Foerderer
 *
 */
public class MeterTopics {
	
	private String prefix;
	
	/**
	 * Constructor
	 * 
	 * Sets up all the topic names.
	 * 
	 * @param topicPrefix Topic topicPrefix, e.g. "fzi.osh".
	 * @param uuid UUID of this device or null to get wildcard topics.
	 */
	public MeterTopics(String prefix) {
		this.prefix = prefix;
	}
	
	/**
	 * Topic for meter state data publishing.
	 * 
	 * @param uuid uuid of meter to observe or <i>null</i> for wildcard topic.
	 */
	public String meterState(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".meter.state";
	}
}
