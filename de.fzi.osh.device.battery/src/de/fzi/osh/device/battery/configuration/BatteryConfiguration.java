package de.fzi.osh.device.battery.configuration;

import com.ghgande.j2mod.modbus.Modbus;

import de.fzi.osh.device.configuration.Configuration;
import de.fzi.osh.time.realtime.RealTimeService;

/**
 * Data structure for smart meter configuration
 * 
 * @author K. Foerderer
 *
 */
public class BatteryConfiguration extends Configuration {
	/**
	 * Nominal capacity of the battery in Wh
	 */
	public int nominalCapacity = 6000;
	/**
	 * Maximum battery SOC [%].
	 * 
	 * This SOC is still tolerated. Action is only taken if SOC > max.
	 */
	public int maxStateOfCharge = 90;
	/**
	 * Minimum battery SOC [%].
	 * 
	 * This SOC is still tolerated. Action is only taken if SOC < min.
	 */
	public int minStateOfCharge = 10;
	/**
	 * Max real power charge flexibilities can provide
	 */
	public int maxFlexibilityCharge = 2000;
	/**
	 * Max real power discharge flexibilities can provide
	 */
	public int maxFlexibilityDischarge = 2000;	
	/**
	 * Size of buffer applied in flexibility creation. 
	 * Since the SOC can still change in between creation an scheduling of an flexibility, 
	 * a (valid) flexibility choice can lead to an illegal SOC.    
	 */
	public int flexibilityEnergyBuffer = 100;
	/**
	 * File used to store the battery scheduler data
	 */
	public String schedulerDataFile = "persistence_battery_scheduler.json";
	/**
	 * Period length for modbus communication in ms
	 */
	public long communicationInterval = 1000;
	/**
	 * Number of communicationIntervals until a new connection is established.
	 * (Frequent reconnection leads to client unexpectedly closing TCP connections. Yet
	 * we want to reconnect periodically to release access)
	 */
	public int reconnectionInterval = 30;
	/**
	 * Scheduler background tasks are executed periodically. Sets period in ms.
	 */
	public long schedulerBackgroundTaskPeriod = 9000;
	/**
	 * Address of the battery interface.
	 */
	public String batteryAddress = "localhost";
	/**
	 * Port for accessing the battery interface.
	 */
	public int batteryPort = Modbus.DEFAULT_PORT;
	/**
	 * Indicates whether battery is connected to one or three phases.
	 * For 3 phase power control registers 36 - 41 are used instead of register 32.
	 */
	public boolean threePhasePowerControl = false;
	/**
	 * Name of time service class to be used.
	 */
	public String timeProvider = RealTimeService.class.getName();
	/**
	 * Switch for more debugging output. (Modbus)
	 */
	public boolean debug = false;
}
