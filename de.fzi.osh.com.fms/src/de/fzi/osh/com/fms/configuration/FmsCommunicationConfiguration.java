package de.fzi.osh.com.fms.configuration;


/**
 * Configuration for the FMS communication service
 * 
 * @author K. Foerderer
 *
 */
public class FmsCommunicationConfiguration {
	/**
	 * Separator to be used in the data link file.
	 */
	public String separator = ";";
	/**
	 * Address of fms server.
	 */
	public String host = "";
	/**
	 * Connection port.
	 */
	public short port = 3333;
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
	public String pushPath = "";
	/**
	 * Base path (<b>absolute path</b>) for retrieving data.
	 */
	public String pullPath = "";
	/***
	 * Time [s] between two attempts to retrieve a new schedule. 
	 */
	public int pollingPeriod = 60 * 5;
	/**
	 * Language tag, e.g. "en", for determining number format to be used for parsing and pushing.
	 */
	public String numberFormatLanguageTag = "en";
	/**
	 * For debugging purposes.
	 * 
	 * Output to console.
	 */
	public boolean debug = false;
}
