package de.fzi.osh.com.fms.implementation;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import com.jcraft.jsch.SftpATTRS;

import de.fzi.osh.com.fms.FmsCommunicationListener;
import de.fzi.osh.com.fms.FmsCommunicationService;
import de.fzi.osh.com.fms.PublicFlexibility;
import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.com.fms.configuration.FmsCommunicationConfiguration;
import de.fzi.osh.com.fms.datalink.DataLink4;
import de.fzi.osh.core.configuration.BaseConfiguration;
import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.core.data.SftpConnection;
import de.fzi.osh.time.TimeService;

@Component(service=FmsCommunicationService.class, immediate=true)
public class FmsCommunication implements FmsCommunicationService, Runnable {
	
	private static Logger log = Logger.getLogger(FmsCommunication.class.getName());

	private List<FmsCommunicationListener> listeners;
	private static ConfigurationService configurationService;	
	private static BaseConfiguration baseConfiguration;
	private static FmsCommunicationConfiguration configuration;
	
	private SftpConnection sftp;
	
	public FmsCommunication() {
		listeners = new ArrayList<FmsCommunicationListener>();
	}
	
	@Override
	public void addListener(FmsCommunicationListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(FmsCommunicationListener listener) {
		listeners.remove(listener);
	}	
	
	@Override
	public boolean publishSchedule(PublicSchedule schedule, PublicFlexibility flexibility, PublicationType type) {
		/*
		 * Rows to publish
		 * 
		 * #_Consumption_Inflexible #_UL
		 * #_FeedIn_Inflexible		#_UE
		 * #_Consumption_Flexible	#_FL
		 * #_FeedIn_Inflexible		#_FE
		 * 
		 * 
		 * #_Power_Min				#_LeistMIN
		 * #_Power_Max				#_LeistMAX
		 * #_Energy_Min				#_EnergieMIN
		 * #_Energy_Max				#_EnergieMAX
		 * 
		 */
		
		if(type != PublicationType.InitialSchedule && type != PublicationType.ScheduleUpdate) {
			log.severe("Invalid publication type.");
			return false;
		}
		
		// check data consistency
		if(schedule.startingTime != flexibility.startingTime) {
			log.severe("Schedule and flexibility starting times do not match.");
			return false;
		}		
		if(schedule.consumption.length != flexibility.powerCorridor.length) {
			log.severe("Time frame of schedule and flexibility data does not match.");
			return false;
		}
				
		ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochSecond(schedule.startingTime), ZoneId.systemDefault());
		
		DataLink4 dataLink = new DataLink4();
		DataLink4.Header header = new DataLink4.Header();
		header.source = "GEMS" + baseConfiguration.virtualSupplyPoint;
		//header.timeZone = "UTC";
		dataLink.setHeader(header);
		
		
		// since the energy boundaries are piecewise linear, the values for each slot have to be set for the end of the time slot.
		// set the starting interval to 0,0
		DataLink4.Row row = new DataLink4.Row();
		row = new DataLink4.Row();
		row.hypothesis = "initial"; // adapted from example file
		row.id = baseConfiguration.virtualSupplyPoint + "_EnergieMIN";
		row.date = time;
		row.resolution = "15min";
		row.unit = "kWh";
		row.value = 0; // Wh -> kWh
		dataLink.addRow(row);

		row = new DataLink4.Row();
		row.hypothesis = "initial"; // adapted from example file
		row.id = baseConfiguration.virtualSupplyPoint + "_EnergieMAX";
		row.date = time;
		row.resolution = "15min";
		row.unit = "kWh";
		row.value = 0; // Wh -> kWh
		dataLink.addRow(row);
		
		for(int i = 0; i < schedule.consumption.length; i++) {
			// schedule
			row = new DataLink4.Row();
			row.hypothesis = "initial"; // adapted from example file
			row.id = baseConfiguration.virtualSupplyPoint + "_UL";
			row.date = time;
			row.resolution = "15min";
			row.unit = "kW";
			row.value = schedule.consumption[i] * 4 / 1000.0; // Wh -> kW
			dataLink.addRow(row);
			
			row = new DataLink4.Row();
			row.hypothesis = "initial"; // adapted from example file
			row.id = baseConfiguration.virtualSupplyPoint + "_UE";
			row.date = time;
			row.resolution = "15min";
			row.unit = "kW";
			row.value = schedule.production[i] * 4 / 1000.0; // Wh -> kW
			dataLink.addRow(row);
			/*
			 * No upload of FL and FE since both are 0, like requested by seven2one
			 * 
			row = new DataLink4.Row();
			row.hypothesis = "initial"; // adapted from example file
			row.id = baseConfiguration.virtualSupplyPoint + "_FL";
			row.date = time;
			row.resolution = "15min";
			row.unit = "kW";
			row.value = schedule.flexibleConsumption[i] * 4 / 1000.0; // Wh -> kW
			dataLink.addRow(row);

			row = new DataLink4.Row();
			row.hypothesis = "initial"; // adapted from example file
			row.id = baseConfiguration.virtualSupplyPoint + "_FE";
			row.date = time;
			row.resolution = "15min";
			row.unit = "kW";
			row.value = schedule.flexibleProduction[i] * 4 / 1000.0; // Wh -> kW
			dataLink.addRow(row);*/
			
			// flexibility
			row = new DataLink4.Row();
			row.hypothesis = "initial"; // adapted from example file
			row.id = baseConfiguration.virtualSupplyPoint + "_LeistMIN";
			row.date = time;
			row.resolution = "15min";
			row.unit = "kW";
			row.value = flexibility.powerCorridor[i].min / 1000.0; // W -> kW
			dataLink.addRow(row);
			
			row = new DataLink4.Row();
			row.hypothesis = "initial"; // adapted from example file
			row.id = baseConfiguration.virtualSupplyPoint + "_LeistMAX";
			row.date = time;
			row.resolution = "15min";
			row.unit = "kW";
			row.value = flexibility.powerCorridor[i].max / 1000.0; // W -> kW
			dataLink.addRow(row);
			
			// since the energy boundaries are piecewise linear, the values for each slot have to be set for the end of the time slot.
			time = time.plusMinutes(15);
			
			row = new DataLink4.Row();
			row.hypothesis = "initial"; // adapted from example file
			row.id = baseConfiguration.virtualSupplyPoint + "_EnergieMIN";
			row.date = time;
			row.resolution = "15min";
			row.unit = "kWh";
			row.value = flexibility.energyCorridor[i].min / 1000.0; // Wh -> kWh
			dataLink.addRow(row);

			row = new DataLink4.Row();
			row.hypothesis = "initial"; // adapted from example file
			row.id = baseConfiguration.virtualSupplyPoint + "_EnergieMAX";
			row.date = time;
			row.resolution = "15min";
			row.unit = "kWh";
			row.value = flexibility.energyCorridor[i].max / 1000.0; // Wh -> kWh
			dataLink.addRow(row);
		}
		
		String data = dataLink.compile(configuration.separator, NumberFormat.getNumberInstance(Locale.forLanguageTag(configuration.numberFormatLanguageTag)));
		if(configuration.debug) {
			// debugging
			System.out.println(data);
			return true;
		} else {
			boolean result = false;
			synchronized (sftp) {
				// connect if necessary
				if(!sftp.isConnected()) {
					sftp.connect();
					// send to data platform
					result = pushData(configuration.pushPath, baseConfiguration.virtualSupplyPoint, type.getFilename(), data, true);
					sftp.disconnect();
				} else {
					// send to data platform
					result = pushData(configuration.pushPath, baseConfiguration.virtualSupplyPoint, type.getFilename(), data, true);					
				}
			}
			return result;
		}
	}
	
	@Override
	public boolean declineScheduleRequest() {
		boolean result = false;
		synchronized (sftp) {
			// connect if necessary
			if(!sftp.isConnected()) {
				sftp.connect();
				// send to data platform
				result = pushData(configuration.pushPath, baseConfiguration.virtualSupplyPoint, PublicationType.ScheduleRequestDenial.getFilename(), " ", true);				
				sftp.disconnect();
			} else {
				// send to data platform
				result = pushData(configuration.pushPath, baseConfiguration.virtualSupplyPoint, PublicationType.ScheduleRequestDenial.getFilename(), " ", true);
			}
		}
		return result;
	}
	
	/**
	 * Pushes a file to the communication platform
	 * 
	 * @param basePath GEMS<->FMS communication directory. Assumed to be existing.
	 * @param folder Folder for this specific GEMS. Created if not existing.  
	 * @param filename Name of file the data is written to.
	 * @param data The actual data to be written.
	 * @param data
	 * @oaram backup Upload a copy of the file as backup
	 */
	public boolean pushData(String basePath, String folder, String filename, String data, boolean backup) {
			
		try {
			// change working path
			sftp.changeDirectory(basePath);			
			if(false ==	sftp.changeDirectory(folder)) {
				log.warning("Could not enter folder'" + folder + "'.");
				// try to create the output folder, if it is not existing yet
				log.info("Creating folder '" + folder + "'");
				sftp.makeDirectory(folder);
				if(false == sftp.changeDirectory(folder)) {
					throw new Exception("Could not enter folder'" + folder + "'.");
				}
			}
			
			if(true == backup) {
				// upload a backup for logging
				String timestamp = timeService.nowAsInstant().toString().replaceAll(":", "-");
				sftp.write(timestamp + "_" + filename, data);
			}
			
			// upload file
			if(false == sftp.write(filename, data)) {
				return false;
			}
			
			return true;
		} catch(Exception e) {
			log.severe("Could not push data to data plattform");
			// [INFO] JSch error message on failure is always just "Failure" without any further detail
			// Nevertheless, there can be other exceptions.
			log.severe(e.getMessage());
			return false;
		}
	}
	
	@Override
	public void run() {
		try {
			// Look if there is a new target schedule
			/*
			 * Order:
			 * 	1. Target PublicSchedule
			 *  2. Target PublicSchedule Update
			 *  3. Requested PublicSchedule
			 * 
			 * Hence, if more than one source is available, the intuitive order is still retained.
			 * 
			 */
			log.finest("Polling FMS data.");
			synchronized(sftp) {
				sftp.connect();
				
				String srcPath = configuration.pullPath + "/" + baseConfiguration.virtualSupplyPoint;
				String destPath = configuration.pushPath + "/" + baseConfiguration.virtualSupplyPoint;
				
				if(false == sftp.changeDirectory(srcPath)) {
					log.severe("Could not enter directory '" + configuration.pullPath + "/" + baseConfiguration.virtualSupplyPoint + "'.");
				}
				
				// timestamp which is added to the file names
				String timestamp = timeService.nowAsInstant().toString().replaceAll(":", "-");
				
				SftpATTRS attributes = sftp.getAttributes(PublicationType.TargetSchedule.getFilename());
				if(null != attributes) {
					// new target schedule
					String content = sftp.read(PublicationType.TargetSchedule.getFilename());

					// parse content
					PublicSchedule schedule = scheduleFromDataLink(content, ZonedDateTime.ofInstant(Instant.ofEpochSecond(attributes.getMTime()), ZoneId.systemDefault()));
					
					sftp.changeDirectory("/");					
					if(null == schedule) {
						// move file 
						sftp.rename(srcPath + "/" + PublicationType.TargetSchedule.getFilename(), 
								destPath + "/" + "FAILED_" + timestamp + "_" + PublicationType.TargetSchedule.getFilename());
						return;
					}					
					log.info("Received new target schedule.");
					
					// notify listeners [before renaming => file isn't marked as read]
					for(FmsCommunicationListener listener : listeners) {
						listener.updateSchedule(schedule);
					}
					
					// add timestamp to filename and move
					sftp.rename(srcPath + "/" + PublicationType.TargetSchedule.getFilename(), 
							destPath + "/" + timestamp + "_" + PublicationType.TargetSchedule.getFilename());
				}
				
				attributes = sftp.getAttributes(PublicationType.TargetScheduleUpdate.getFilename());				
				if(null != attributes) {
					// new target schedule
					String content = sftp.read(PublicationType.TargetScheduleUpdate.getFilename());
					
					// parse content
					PublicSchedule schedule = scheduleFromDataLink(content, ZonedDateTime.ofInstant(Instant.ofEpochSecond(attributes.getMTime()), ZoneId.systemDefault()));
					sftp.changeDirectory("/");
					if(null == schedule) {
						// move file
						sftp.rename(srcPath + "/" + PublicationType.TargetScheduleUpdate.getFilename(), 
								destPath + "/" + "FAILED_" + timestamp + "_" + PublicationType.TargetScheduleUpdate.getFilename());
						return;
					}					
					log.info("Received target schedule update.");
					
					// notify listeners [before renaming => file isn't marked as read]
					for(FmsCommunicationListener listener : listeners) {
						listener.updateSchedule(schedule);
					}
					
					// add timestamp to filename and move
					sftp.rename(srcPath + "/" + PublicationType.TargetScheduleUpdate.getFilename(), 
							destPath + "/" + timestamp + "_" + PublicationType.TargetScheduleUpdate.getFilename());
				}
				
				attributes = sftp.getAttributes(PublicationType.ScheduleRequest.getFilename());				
				if(null != attributes) {
					// schedule request
					String content = sftp.read(PublicationType.ScheduleRequest.getFilename());
					
					// parse content
					PublicSchedule schedule = scheduleFromDataLink(content, ZonedDateTime.ofInstant(Instant.ofEpochSecond(attributes.getMTime()), ZoneId.systemDefault()));
					sftp.changeDirectory("/");
					if(null == schedule) {
						// move file
						sftp.rename(srcPath + "/" + PublicationType.ScheduleRequest.getFilename(), 
								destPath + "/" + "FAILED_" + timestamp + "_" + PublicationType.ScheduleRequest.getFilename());
						return;
					}					
					log.info("Received new schedule request.");
					
					// notify listeners [before renaming => file isn't marked as read]
					for(FmsCommunicationListener listener : listeners) {
						listener.requestSchedule(schedule);
					}
					
					// add timestamp to filename and move
					sftp.rename(srcPath + "/" + PublicationType.ScheduleRequest.getFilename(), 
							destPath + "/" + timestamp + "_" + PublicationType.ScheduleRequest.getFilename());
				}
				
				sftp.disconnect();
			}
			
			log.finest("Finished polling FMS data.");
		} catch(Exception e) {
			log.severe("FMS polling failed.");
			log.severe(e.toString());
			try {
				if(sftp.isConnected()) {
					sftp.disconnect();
				}
			} catch(Exception e2) {
				
			}
		}
	}
	
	/**
	 * Helper function for creating a schedule from a datalink file
	 * 
	 * @param data
	 * @param modificationTime
	 * @return
	 */
	private PublicSchedule scheduleFromDataLink(String data, ZonedDateTime modificationTime) {
		// parse data
 		DataLink4 file = new DataLink4();
		try {
			file.parse(data, configuration.separator, NumberFormat.getNumberInstance(Locale.forLanguageTag(configuration.numberFormatLanguageTag)));
		} catch(Exception e) {
			log.severe("Parsing of schedule failed.");
			log.severe(e.toString());
			return null;
		}
		
		if(!file.getHeader().source.equals(baseConfiguration.energySupplier)) {
			log.warning("Schedule source is '" + file.getHeader().source + "', but expected '" + baseConfiguration.energySupplier + "'.");
		}
		
		// read schedule
		PublicSchedule schedule = new PublicSchedule();
		schedule.timestamp = modificationTime.toEpochSecond();
		schedule.startingTime = -1;
		
		// get starting time (in case data is not chronologically ordered)
		for(DataLink4.Row row : file.getRows()) {
			if(0 >= schedule.startingTime || schedule.startingTime > row.date.toEpochSecond()) {
				schedule.startingTime = row.date.toEpochSecond();
			}
		}

		// initialize arrays
		schedule.consumption = new int[file.getRows().size() / 4]; // there are 4 rows for each value
		schedule.production = new int[schedule.consumption.length];
		schedule.flexibleConsumption = new int[schedule.consumption.length];
		schedule.flexibleProduction = new int[schedule.consumption.length];
		
		schedule.slotLength = 15 * 60;
		String recentUnknownSeries = "";
		for(DataLink4.Row row : file.getRows()) {
			// determine slot
			long secondsSinceStart = row.date.toEpochSecond() - schedule.startingTime;
			
			if(secondsSinceStart % (schedule.slotLength) != 0){
				log.severe("Time step does not equal 15 min.");
				return null;
			}
			if(secondsSinceStart / (schedule.slotLength) > schedule.consumption.length) {
				log.severe("Time is after expected schedule end. Are there rows missing?");
				return null;
			}
			
			// map data
			if(row.id.equalsIgnoreCase(baseConfiguration.virtualSupplyPoint + "_UL")) {
				schedule.consumption[(int)(secondsSinceStart / (schedule.slotLength))] = (int) (row.value * 1000 * (schedule.slotLength / (60.0 * 60))); // kW -> Wh
			}
			else if(row.id.equalsIgnoreCase(baseConfiguration.virtualSupplyPoint + "_UE")) {
				schedule.production[(int)(secondsSinceStart / (schedule.slotLength))] = (int) (row.value * 1000 * (schedule.slotLength / (60.0 * 60))); // kW -> Wh
			}
			else if(row.id.equalsIgnoreCase(baseConfiguration.virtualSupplyPoint + "_FL")) {
				schedule.flexibleConsumption[(int)(secondsSinceStart / (schedule.slotLength))] = (int) (row.value * 1000 * (schedule.slotLength / (60.0 * 60))); // kW -> Wh
			}
			else if(row.id.equalsIgnoreCase(baseConfiguration.virtualSupplyPoint + "_FE")) {
				schedule.flexibleProduction[(int)(secondsSinceStart / (schedule.slotLength))] = (int) (row.value * 1000 * (schedule.slotLength / (60.0 * 60))); // kW -> Wh
			} else  {
				// unknown series. Wrong supply point?
				if(!recentUnknownSeries.equals(row.id)) {
					log.warning("Unknown time series: " + row.id + ". Check virtual supply point!");
					recentUnknownSeries = row.id;
				}
			}
		}
		
		// not needed anymore
		/* aggregate target schedule into consumption array
		for(int i = 0; i < schedule.consumption.length; i++) {
			schedule.consumption[i] = schedule.consumption[i] - schedule.production[i] + schedule.flexibleConsumption[i] - schedule.flexibleProduction[i]; 
		}
		schedule.production = null;
		schedule.flexibleConsumption = null;
		schedule.flexibleProduction = null;*/
		
		return schedule;
	}
	
	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		FmsCommunication.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		FmsCommunication.configurationService = null;
	}
	
	private static TimeService timeService;
	@Reference(
			name = "TimeService",
			service = TimeService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeService"
		)	
	protected synchronized void bindTimeService(TimeService timeService) {
		FmsCommunication.timeService = timeService;
	}
	protected synchronized void unbindTimeService(TimeService timeService) {
		FmsCommunication.timeService = null;
	}
	
	@Activate
	protected synchronized void activate() throws Exception {
		baseConfiguration = configurationService.get(BaseConfiguration.class);
		configuration = configurationService.get(FmsCommunicationConfiguration.class);
		
		/* DEBUGGING */ /*
		PublicSchedule test = new PublicSchedule();		
		test.timestamp = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
		test.startingTime = ZonedDateTime.of(test.timestamp.getYear(), test.timestamp.getMonthValue(), test.timestamp.getDayOfMonth(), 0, 0, 0, 0, ZoneId.systemDefault());
		test.consumption = new int[] {5,10,-3,51};
		test.flexibleConsumption = new int [] {100, -200, 120, 110};
		
		PublicFlexibility test2 = new PublicFlexibility();
		test2.timestamp = test.timestamp;
		test2.startingTime = test.startingTime;
		test2.powerCorridor = new IntInterval[4];
		test2.energyCorridor = new IntInterval[4];
		
		for(int i = 0; i < test2.powerCorridor.length; i++) {
			test2.powerCorridor[i] = new IntInterval(-i, i);
			test2.energyCorridor[i] = new IntInterval(-i*10, i*10);
		}*/
		
		sftp = new SftpConnection(configuration.host, configuration.port, configuration.user, configuration.password);
		
		/* DEBUGGING */ /*
		publishSchedule(test, test2, PublicationType.InitialSchedule);*/
		
		timeService.scheduleAtRate(this, 0, configuration.pollingPeriod * 1000);		
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
	}
}
