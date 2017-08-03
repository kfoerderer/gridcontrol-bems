package de.fzi.osh.com.control.configuration;

/**
 * Configuration for the VNB communication service
 * 
 * @author K. Foerderer
 *
 */
public class ControlCommunicationConfiguration {
	/**
	 * Address of fms server.
	 */
	public String host = "";
	/**
	 * Connection port.
	 */
	public short port = 0;
	/**
	 * User name.
	 */
	public String user = "";
	/**
	 * Password.
	 */
	public String password = "";
	/**
	 * Base path (<b>absolute path</b>) for data storage.
	 */
	public String path = "";
	/***
	 * Time [s] between two attempts to retrieve new commands. 
	 */
	public int pollingPeriod = 60 * 5;
	/**
	 * For debugging purposes.
	 * 
	 * Output to console.
	 */
	public boolean debug = false;
}
