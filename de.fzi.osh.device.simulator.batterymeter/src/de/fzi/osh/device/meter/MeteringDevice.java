package de.fzi.osh.device.meter;

import java.io.FileInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import de.fzi.osh.core.data.Json;
import de.fzi.osh.device.Device;
import de.fzi.osh.device.meter.configuration.MeterConfiguration;
import de.fzi.osh.device.meter.data.SmartMeterData;
import de.fzi.osh.device.time.Time;
import de.fzi.osh.time.TimeService;
import de.fzi.osh.time.realtime.RealTimeService;

public class MeteringDevice extends Device<MeteringDevice, MeterConfiguration>  {

	private static Logger log = Logger.getLogger(MeteringDevice.class.getName());
	
	private MeterCommunication communication;
	
	private int previousTotalActivePower = 0; // used inertance
	private long energyPcWh = 0; // Wh / 100
	private int energyPmWs = 0;
	private long energyNcWh = 0; // Wh / 100
	private int energyNmWs = 0;
	
	public MeteringDevice(MeterConfiguration configuration) {
		super(configuration);
		
		// initialize OC
		controller = new MeteringDeviceController(this);
		observer = new MeteringDeviceObserver(this, controller);
		
		// connect to communication
		communication = new MeterCommunication(this);
		communication.open();

			
		// create scheduler and measure every $configuration.samplingInterval ms
		Time.service().scheduleAtRate(this, 0, configuration.samplingInterval);
	}

	private Instant previousInstant = Time.service().nowAsInstant();
	
	@Override
	public void run() {
		// the actual driver
		
		try {
			// read meter and pass to observer			
			SmartMeterData meterData = new SmartMeterData();
			meterData.time = Time.service().nowAsInstant();
			
			// determine simulated meter data
			communication.getBatteryActivePower(response -> {
				
					meterData.totalActivePower = previousTotalActivePower;
					previousTotalActivePower = -response.realPower * 10;
					
					meterData.totalReactivePower = 0; // only active power is simulated					
					long millis = Duration.between(previousInstant, meterData.time).toMillis();
					
					if(meterData.totalActivePower < 0) {
						// mWs = 10W * ms / 10
						energyNmWs += meterData.totalActivePower * millis / 10;
						if(energyNmWs <= 60*60*1000 / 100) {
							energyNcWh -= 100 * energyNmWs / (60*60*1000); // Wh/100
							energyNmWs %= 60*60*1000 / 100;
						}
					} else {
						energyPmWs += meterData.totalActivePower * millis / 10;
						if(energyPmWs >= 60*60*1000 / 100) {
							energyPcWh += 100 * energyPmWs / (60*60*1000); // Wh/100
							energyPmWs %= 60*60*1000 / 100;
						}
					}
					
					meterData.totalActiveEnergyP = energyPcWh;			
					meterData.totalActiveEnergyN = energyNcWh;
					meterData.alarmFlag = 0;
					
					previousInstant = meterData.time;
					
					observer.update(meterData);
				},
				error -> {
				},
				() -> {
				});
		} catch(Exception e) {
			log.severe("An exception has been caught!");
			log.severe(e.toString());
			e.printStackTrace();
		}
	}
	
	public MeterCommunication getBusConnection(){
		return communication;
	}
	
	public static void main(String args[]) {
		
		// load logger configuration
        try {
			LogManager.getLogManager().readConfiguration(new FileInputStream("logging.properties"));
		} catch (Exception e) {
			System.out.println("Error loading logger configuration:");
			e.printStackTrace();
		}
		
		// load configuration file	
		log.info("Loading configuration: " + args[0]);
		MeterConfiguration configuration = Json.readFile(args[0], MeterConfiguration.class);
		
		// initialize time service
		TimeService timeService;
		try {
			// try to load the given class
			timeService = (TimeService) ClassLoader.getSystemClassLoader().loadClass(configuration.timeProvider).newInstance();
			Time.initialize(timeService);
		} catch (Exception e) {
			e.printStackTrace();
			// could not load the class => use real time
			Time.initialize(new RealTimeService());
		}		
		
		Device<MeteringDevice, MeterConfiguration> device = new MeteringDevice(configuration);
		
		// clean up on shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				device.shutdown();
			}
		});		
	}
}
