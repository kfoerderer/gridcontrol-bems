package de.fzi.osh.wamp.rems;

import java.util.UUID;

/**
 * Interface for Rems routing.
 * 
 * 
 * @author Foerderer K.
 *
 */
public class RemsTopics {

	/**
	 * Topic prefix.
	 */
	private String prefix;
	
	/**
	 * Constructor.
	 * 
	 * @param prefix
	 */
	public RemsTopics(String prefix) {
		this.prefix = prefix;
	}
	
	/**
	 * Rems control signals for battery
	 */
	public String controlSignal(UUID uuid) {
		return prefix + "." + (uuid == null ? "" : uuid.toString()) + ".rems.control";
	}
	
}
