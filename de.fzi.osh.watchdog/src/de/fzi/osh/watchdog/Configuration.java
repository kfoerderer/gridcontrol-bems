package de.fzi.osh.watchdog;

import java.util.UUID;

import de.fzi.osh.wamp.configuration.WampConfiguration;

/**
 * Watchdog configuration.
 * 
 * @author Foerderer K.
 *
 */
public class Configuration {
	/**
	 * Wamp configuration.
	 */
	public WampConfiguration wamp;
	/**
	 * Monitoring rate in seconds.
	 */
	public long monitoringRate = 30;
	/**
	 * Initial delay before first monitoring attempt in seconds.
	 */
	public long initialDelay = 120;
	/**
	 * After restarting a component subsequent runs are skipped until this count is reached.
	 */
	public int restartSkippingCount = 3; // omit 3 => 4 * 30 sec till next run
	
	/**
	 * DB server url.
	 */
	public String databaseUrl;
	/**
	 * DB user name.
	 */
	public String databaseUser;
	/**
	 * DB password.
	 */
	public String databasePassword;
	/**
	 * DB name.
	 */
	public String databaseName;
	
	/**
	 * UUIDs of batteries.
	 */
	public UUID[] batteries;
	/**
	 * UUIDs of meters.
	 */
	public UUID[] meters;
	
	/**
	 * URL of gui. 
	 */
	public String guiUrl = "http://localhost:8888/";
	
	/**
	 * File name of scheduler's persistence file to be monitored.
	 */
	public String schedulerPersistenceFile = ""; 
	
	/**
	 * Command to be executed for restarting crossbar.
	 */
	public String commandRestartCrossbar = "cmd /c start cmd.exe /K echo restart crossbar";
	/**
	 * Command to be executed for restarting device drivers.
	 */
	public String commandRestartMeterDrivers = "cmd /c start cmd.exe /K echo restart meter drivers";
	/**
	 * Commmand to be executed for restarting battery drivers.
	 */
	public String commandRestartBatteryDrivers = "cmd /c start cmd.exe /K echo restart battery drivers";
	/**
	 * Command to be executed for restarting mysql.
	 */
	public String commandRestartDatabase = "cmd /c start cmd.exe /K echo restart mysql";
	/**
	 * Command to be executed for restarting bems.
	 */
	public String commandRestartBems = "cmd /c start cmd.exe /K echo restart bems";
}
