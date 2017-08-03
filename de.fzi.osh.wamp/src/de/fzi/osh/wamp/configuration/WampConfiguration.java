package de.fzi.osh.wamp.configuration;

/**
 * Configuration for WAMP client.
 * 
 * @author K. Foerderer
 *
 */
public class WampConfiguration {
	/**
	 * Server url.
	 */
	public String url;
	/**
	 * Realm.
	 */
	public String realm;
	/**
	 * Topic prefix.
	 */
	public String topicPrefix;
	/**
	 * Max payload size for a websocket frame. Default value is 64kB taken from jawampa default netty configuration.
	 */
	public int maxFramePayloadLength = 65535;
}
