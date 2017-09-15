package de.fzi.osh.watchdog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import de.fzi.osh.core.data.Json;
import de.fzi.osh.wamp.device.battery.BatteryState;

/**
 * Watchdog for monitoring crossbar, device drivers, database and gui. 
 * 
 * @author Foerderer K.
 *
 */
public class Application implements Runnable{
	
	private Logger log = Logger.getLogger(Application.class.getName());

	private Configuration configuration;
	private Bus bus;
	private int skippingCount = 0;
	
	private InfluxDB influxConn;
	
	public Application(Configuration configuration) {
		this.configuration = configuration;
		bus = new Bus(this.configuration);
		bus.connect();
		
		if(configuration.schedulerPersistenceFile.length() == 0) {
			log.info("Will not check scheduler persistence.");
		}
	}
	
	public static void printHelp() {
		System.out.println("Usage: watchdog <configuration>");
		System.out.println("configuration: \tconfiguration file");
	}
	
	public static void main(String args[]) {
		
		// load logger configuration
        try {
			LogManager.getLogManager().readConfiguration(new FileInputStream("logging.properties"));
		} catch (Exception e) {
			System.out.println("Error loading logger configuration:");
			e.printStackTrace();
		}
		
		Configuration configuration;
		String configurationFile;	
		
		if(args.length == 0) {
			printHelp();
			return;
		}
		else if(args.length == 1) {
			configurationFile = args[0];
			configuration = Json.readFile(configurationFile, Configuration.class);
			if(configuration == null) {
				printHelp();
				return;
			}
		} else {
			printHelp();
			return;
		}
		
		Application watchdog = new Application(configuration);
		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
		
		executorService.scheduleAtFixedRate(watchdog, configuration.initialDelay, configuration.monitoringRate, TimeUnit.SECONDS);
	}

	@Override
	public void run() {		
		try {
			
			if(skippingCount > 0) {
				skippingCount--;
				return;
			}
			
			// check crossbar
			if(false == isCrossbarRunning(5)) {
				log.severe("Restarting crossbar");
				try {
					Runtime.getRuntime().exec(configuration.commandRestartCrossbar);
				} catch (IOException e) {
					e.printStackTrace();
				}
				skippingCount = configuration.restartSkippingCount;
				// needed for further checks
				return;
			}
			// check metering devices
			if(false == areMeterDriversRunning(30)) {
				log.severe("Restarting meter drivers");
				try {
					Runtime.getRuntime().exec(configuration.commandRestartMeterDrivers);
				} catch (IOException e) {
					e.printStackTrace();
				}
				skippingCount = configuration.restartSkippingCount;
			}
			// check batteries
			if(false == areBatteryDriversRunning()) {
				log.severe("Restarting battery drivers");
				try {
					Runtime.getRuntime().exec(configuration.commandRestartBatteryDrivers);
				} catch (IOException e) {
					e.printStackTrace();
				}
				skippingCount = configuration.restartSkippingCount;
			}
			// check database
			// make sure db connection is closed
			if(null != influxConn) {
				influxConn.close();
				influxConn = null;
			}
			// this function will open a new db connection
			if(false == isDatabaseRunning(5)) {
				log.severe("Restarting database");
				try {
					Runtime.getRuntime().exec(configuration.commandRestartDatabase);
				} catch (IOException e) {
					e.printStackTrace();
				}
				skippingCount = configuration.restartSkippingCount;
				// BEMS check needs DB
				return;
			}
			// check BEMS
			if(false == isBemsRunning()) {
				log.severe("Restarting bems");
				try {
					Runtime.getRuntime().exec(configuration.commandRestartBems);
				} catch (IOException e) {
					e.printStackTrace();
				}
				skippingCount = configuration.restartSkippingCount;
			}
			// close db connection
			if(null != influxConn) {
				influxConn.close();
				influxConn = null;
			}
		} catch(Exception e) {
			log.severe("Exception: " + e.toString());
			e.printStackTrace();
		}
	}
	
	/**
	 * Checks whether crossbar is running or not.
	 * 
	 * @param attemptsOnFail
	 * @return
	 */
	private boolean isCrossbarRunning(int attemptsOnFail) {
		Instant now = Instant.now();
		String in = now.toString();
		String out = bus.echo(in);
		if(!out.equals(in)) {
			log.severe("echo failed");
			if(attemptsOnFail > 0) {
				log.info("Trying " + attemptsOnFail + " more times.");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return isCrossbarRunning(attemptsOnFail - 1);
			} else {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Checks if meter drivers still push data.
	 * 
	 * @param timeBuffer time window in seconds
	 * @return
	 */
	private boolean areMeterDriversRunning(int timeBuffer) {
		Map<UUID, Long> mostRecentMeterData = bus.getMostRecentMeterData();
		
		Long now = Instant.now().getEpochSecond();
		for(UUID uuid : configuration.meters) {
			Long time = mostRecentMeterData.get(uuid);
			if(time == null || time + timeBuffer < now) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Checks if battery drivers respond.
	 * 
	 * @return
	 */
	private boolean areBatteryDriversRunning() {
		// check each battery
		for(UUID uuid : configuration.batteries) {
			BatteryState state = bus.getBatteryState(uuid);
			if(null == state) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Checks if the database is up.
	 * 
	 * @return
	 */
	private boolean isDatabaseRunning(int attemptsOnFail) {
		try {
		
			influxConn = InfluxDBFactory.connect(	configuration.databaseUrl,
													configuration.databaseUser,
													configuration.databasePassword);			 
			 if(influxConn.ping().getVersion().equalsIgnoreCase("unknown")) {
				 return false;
			 }
			 return true;
		} catch (Exception e) {
			log.severe("Database connection failed.");
			influxConn = null;
			return false;
		}
	}
	
	/**
	 * Checks if BEMS is running.
	 * 
	 * (no tests for upload/download related stuff to save bandwidth)
	 * 
	 * @return
	 */
	private boolean isBemsRunning() {
		// test data logger
		{
			String queryStat = "";
			try {
				// choose a random index to monitor all meters
				int index = (int)(Math.random() * configuration.meters.length);

				String where = "time >= now() - 30s";
				String table = configuration.meters[index].toString() + "_MeterData";
				String interval = "1s";
				String selection = "LAST(totalActivePower)";
				queryStat = "SELECT " + selection + " FROM \"" + table + "\" WHERE " + where + " GROUP BY time(" + interval + ")" + " fill(none) LIMIT 1";
				Query query = new Query(queryStat, configuration.databaseName);
				QueryResult queryResult = influxConn.query(query,TimeUnit.MILLISECONDS);
				
				if(queryResult.hasError() || queryResult.getResults().isEmpty()) {
					return false;
				}
				 
			} catch (Exception e) {
				log.severe("Database error.");
				log.info(queryStat);
				log.severe(e.toString());
				e.printStackTrace();
				return false;
			}
		}
		
		// test GUI
		{
			Scanner scanner = null;
			try {
				if(configuration.guiUrl.length() > 0) {
					URL url = new URL(configuration.guiUrl);
					URLConnection connection = url.openConnection();
					InputStream stream = connection.getInputStream();
					scanner = new Scanner(stream);
					String response = "";
					while(scanner.hasNextLine()) {
						response += scanner.nextLine() + System.lineSeparator();
					}
					if(response.indexOf("autobahn.js") < 0) {
						log.severe("GUI content not as expected.");
						return false;
					}
				}
			} catch (MalformedURLException e) {
				log.warning("GUI url invalid. Check configuration.");
			} catch (Exception e) {
				log.severe(e.toString());
				return false;
			} finally {
				if(null != scanner) {
					scanner.close();
				}
			}
		}
		
		// test Scheduler
		if(configuration.schedulerPersistenceFile.length() > 0)
		{
			// is the persistence file still used? There has to be an update at least every 25 hours.
			File file = new File(configuration.schedulerPersistenceFile);
			Instant lastModified = Instant.ofEpochMilli(file.lastModified());
			if(Duration.between(lastModified, Instant.now()).toHours() > 25) {
				log.severe("Persistence file missing or outdated: " + configuration.schedulerPersistenceFile);
				return false;
			}
		}
		
		return true;
	}
}
