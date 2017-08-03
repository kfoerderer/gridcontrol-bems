package de.fzi.osh.wamp;

import java.util.logging.Logger;

import de.fzi.osh.core.component.OshComponent;
import de.fzi.osh.core.component.OshComponentConfiguration;
import de.fzi.osh.wamp.configuration.WampConfiguration;

/**
 * Base class for communication implementation. CommunicationInterface happens asynchronous.
 * 
 * @author K. Foerderer
 *
 * @param <T> OshComponent
 * @param <C> Configuration
 */
public abstract class CommunicationInterface<T extends OshComponent<T, C>, C extends OshComponentConfiguration> {
	
	private static Logger log = Logger.getLogger(CommunicationInterface.class.getName());
	
	protected Connection connection;
		
	protected T component;
	protected C configuration;
	protected WampConfiguration wampConfiguration;
	
	/**
	 * Constructs communication channel for the given device.
	 * 
	 * @param component
	 */
	public CommunicationInterface(T component, WampConfiguration wampConfiguration) {
		this.component = component;
		this.configuration = component.getConfiguration();
		this.wampConfiguration = wampConfiguration;
		
		connection = new Connection(wampConfiguration.url, wampConfiguration.realm, wampConfiguration.maxFramePayloadLength, component.getClass().getName());
	}
	
	/**
	 * Connect to communication bus.
	 */
	public void open() {
		// build client
		log.info("Trying to connect to realm " + wampConfiguration.realm + " on " + wampConfiguration.url + ".");
		try {
			connection.open();
		} catch (Exception e) {
			log.severe("Openning connection failed.");
			log.severe(e.toString());
			return;
		}
	}
}
