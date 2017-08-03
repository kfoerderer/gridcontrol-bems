package de.fzi.osh.data.upload.configuration;

import java.util.Map;
import java.util.UUID;

public class UploaderConfiguration {
	/**
	 * Separator to be used in the data link file.
	 */
	public String separator = ";";
	/**
	 * Language tag, e.g. "en", for determining number format to be used for parsing and pushing.
	 */
	public String numberFormatLanguageTag = "en";
	/**
	 * Mapping of device uuids onto the names used to represent the device in the time series.
	 */
	public Map<UUID, String> deviceNames;
	/**
	 * Defines the hour the upload will executed. Minute and second will be selected by random at startup.
	 */
	public int uploadHour = 3;
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
	/**
	 * For debugging purposes.
	 * 
	 * Output to console.
	 */
	public boolean debug = false;
}
