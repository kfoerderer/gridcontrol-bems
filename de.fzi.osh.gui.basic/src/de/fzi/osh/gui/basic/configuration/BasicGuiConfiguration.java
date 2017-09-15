package de.fzi.osh.gui.basic.configuration;

/**
 * Class holding all gui configuration data.
 * 
 * @author K. Foerderer
 *
 */
public class BasicGuiConfiguration {
	/**
	 * GUI connects to this substitute WAMP host address instead of the address given by the WAMP configuration, if set.
	 * This is needed to deal with the address 'localhost' in the WAMP configuration. 
	 */
	public String substituteWampHost = ""; 
	
	/**
	 * Server port for incoming http requests.
	 */
	public int port = 8888;
	
	/**
	 * Directory containing gui.
	 */
	public String webPath = "../de.fzi.osh.gui.basic/web/standard";
	
	/**
	 * Boundaries for battery power to electricity network applied to determine if the flexibility is used or not.
	 */
	public int flexibilityMessageBuffer = 150;
}
