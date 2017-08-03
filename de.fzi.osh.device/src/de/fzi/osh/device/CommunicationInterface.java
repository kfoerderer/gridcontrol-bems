package de.fzi.osh.device;

import java.util.logging.Logger;

import de.fzi.osh.device.configuration.Configuration;
import de.fzi.osh.wamp.Connection;

/**
 * Base class for communication implementation. CommunicationInterface happens asynchronous.
 * 
 * @author K. Foerderer
 *
 * @param <T> OshComponent
 * @param <C> Configuration
 */
public abstract class CommunicationInterface<D extends Device<D,C>, C extends Configuration> {
	
	private static Logger log = Logger.getLogger(CommunicationInterface.class.getName());
	
	protected Connection connection;
	
	protected D device;
	protected C configuration;
	
	/**
	 * Constructs communication channel for the given device.
	 * 
	 * @param device
	 */
	public CommunicationInterface(D device) {
		this.device = device;
		this.configuration = device.getConfiguration();

		connection = new Connection(configuration.wamp.url, configuration.wamp.realm, configuration.wamp.maxFramePayloadLength, device.getClass().getName());
	}
	
	/**
	 * Connect to communication bus.
	 */
	public void open() {
		// build client
		log.info("Trying to connect to realm " + configuration.wamp.realm + " on " + configuration.wamp.url + ".");
		try {
			connection.open();
		} catch (Exception e) {
			log.severe("Openning connection failed.");
			log.severe(e.getMessage());
			return;
		}
	}
}
