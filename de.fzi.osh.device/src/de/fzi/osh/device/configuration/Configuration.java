package de.fzi.osh.device.configuration;

import java.util.UUID;

import de.fzi.osh.core.component.OshComponentConfiguration;
import de.fzi.osh.wamp.configuration.WampConfiguration;

/**
 * Configuration base class.
 * 
 * @author K. Foerderer
 *
 */
public class Configuration extends OshComponentConfiguration{
	
	public Configuration() {
	}
	
	/**
	 * Device UUID.
	 */
	public UUID uuid;
	
	/**
	 * WAMP configuration.
	 */
	public WampConfiguration wamp;
}
