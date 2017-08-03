package de.fzi.osh.wamp.device.battery;

import java.util.UUID;

/**
 * Topics for battery devices.
 * 
 * @author K. Foerderer
 *
 */
public class BatteryTopics {
	
	private String prefix;
	
	/**
	 * Constructor
	 * 
	 * Sets up all the topic names.
	 * 
	 * @param topicPrefix Topic topicPrefix, e.g. "fzi.osh".
	 * @param uuid UUID of this device or null to get wildcard topics.
	 */
	public BatteryTopics(String prefix) {
		this.prefix = prefix;
	}
	
	/**
	 * Topic for requesting battery state.
	 */
	public String getBatteryState(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".battery.state.get";
	}
	
	/**
	 * Topic for setting battery real power.
	 */
	public String setRealPower(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".battery.realpower.set";
	}
	
	/**
	 * Topic for setting a target soc.
	 */
	public String setTargetSoc(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".battery.target.set";
	}
	
	/**
	 * Topic for SOC publications. Set uuid to <i>null</i> to get the wildcard topic.
	 * 
	 * @param uuid
	 * @return
	 */
	public String soc(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".battery.soc";
	}
}
