package de.fzi.osh.data.upload;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.com.fms.datalink.DataLink4;
import de.fzi.osh.core.configuration.BaseConfiguration;
import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.core.data.SftpConnection;
import de.fzi.osh.data.logging.types.BatterySocData;
import de.fzi.osh.data.logging.types.MeterData;
import de.fzi.osh.data.storage.timeseries.TimeSeriesStorageService;
import de.fzi.osh.data.storage.timeseries.TimeSeries;
import de.fzi.osh.data.upload.configuration.UploaderConfiguration;
import de.fzi.osh.time.TimeService;

@Component(enabled=true,immediate=true)
public class Uploader implements Runnable{
	
	private static Logger log = Logger.getLogger(Uploader.class.getName());
	
	private TimeService timeService;
	private ConfigurationService configurationService;	
	private TimeSeriesStorageService timeSeriesStorageService;
	
	private BaseConfiguration baseConfiguration;
	private UploaderConfiguration configuration;
	
	private ScheduledFuture<?> task;
	
	private SftpConnection sftp;
	
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
		// collect and upload data
		try {			
			ZonedDateTime end = timeService.nowAsZonedDateTime().truncatedTo(ChronoUnit.DAYS);
			ZonedDateTime start = end.minus(1, ChronoUnit.DAYS);
			
			// create a set containing all meter ids
			Set<UUID> meters = new HashSet<UUID>();
			Collections.addAll(meters, baseConfiguration.consumptionMeterUUIDs);
			Collections.addAll(meters, baseConfiguration.productionMeterUUIDs);
			Collections.addAll(meters, baseConfiguration.batteryMeterUUIDs);

			// content
			DataLink4 dataLink = new DataLink4();
			DataLink4.Header header = new DataLink4.Header();
			header.source = "GEMS" + baseConfiguration.virtualSupplyPoint;
			//header.timeZone = "UTC";
			dataLink.setHeader(header);
			DataLink4.Row row;			
			
			TimeSeries series = MeterData.class.getAnnotation(TimeSeries.class);
			for(UUID uuid : meters) {
				// retrieve meter name if available
				String meterName;
				if(null == configuration.deviceNames || null == configuration.deviceNames.get(uuid)) {
					log.warning("No device name specified for '" + uuid + "'.");	
					meterName = uuid.toString();
				} else {
					meterName = configuration.deviceNames.get(uuid);
				}
				
				// get data from db
				List<MeterData> rawData;
				try {
										
					rawData = timeSeriesStorageService.select("time <= " + end.toEpochSecond() + "s" + " AND time >= " + start.toEpochSecond() + "s", 
															uuid + "_" + series.name(),
															MeterData.class,
															"1m");					
					
				} catch (Exception e) {
					log.severe("Collecting meter data failed for meter '" + uuid + "'.");
					log.severe(e.toString());
					continue;
				}
							
				if(null == rawData) {
					log.warning("No meter data available for '" + uuid + "'.");
					continue;
				}
				
				// process data
				// collect (energy) data in a map
				long startSecond = start.toEpochSecond();		

				Map<Integer, Long[]> minuteData = new HashMap<Integer, Long[]>();
				for(MeterData meterData : rawData) {					 
					// determine which minute this is 
					long second = meterData.time.getEpochSecond();
					int minute = (int) ((second - startSecond) / 60);				
					Long[] values = minuteData.get(minute);
					if(null == values) {
						// no data yet for this minute
						// data is sorted by timestamp, so the earliest value comes first
						values = new Long[2];
						values[0] = meterData.totalActiveEnergyP;
						values[1] = meterData.totalActiveEnergyN;
						minuteData.put(minute, values);
						
						// create data-link entries
						if(values[0] != 0) {
							row = new DataLink4.Row();
							row.hypothesis = "REF"; // adapted from example file
							row.id = baseConfiguration.virtualSupplyPoint + "_" + meterName + "_P";
							row.date = meterData.time.atZone(ZoneId.systemDefault());
							row.resolution = "min";
							row.unit = "Wh";
							row.value = values[0] / 100.0; // 1/100 Wh -> Wh
							dataLink.addRow(row);
						}
						
						if(values[1] != 0) {
							row = new DataLink4.Row();
							row.hypothesis = "REF"; // adapted from example file
							row.id = baseConfiguration.virtualSupplyPoint + "_" + meterName + "_N";
							row.date = meterData.time.atZone(ZoneId.systemDefault());
							row.resolution = "min";
							row.unit = "Wh";
							row.value = -values[1] / 100.0; // 1/100 Wh -> Wh
							dataLink.addRow(row);
						}
					}
					// Since the total energy value is stored, there is no need to store the power inbetween to minutes.
					// The average power can be derived from the value of the current minute and the value of the next minute.
				}
			}
			
			// now battery SOCs
			series = BatterySocData.class.getAnnotation(TimeSeries.class);
			for(UUID uuid : baseConfiguration.batteryUUIDs) {
				
				// retrieve battery name if available
				String batteryName;
				if(null == configuration.deviceNames || null == configuration.deviceNames.get(uuid)) {
					log.warning("No device name specified for '" + uuid + "'.");	
					batteryName = uuid.toString();
				} else {
					batteryName = configuration.deviceNames.get(uuid);
				}
				
				List<BatterySocData> rawData;
				try {
					
					// upload data "as is"
					rawData = timeSeriesStorageService.select("time < " + end.toEpochSecond() + "s" + " AND time >= " + start.toEpochSecond() + "s", 
							uuid + "_" + series.name(),
							BatterySocData.class,
							"1s");
					
				} catch (Exception e) {
					log.severe("Collecting soc data failed for battery '" + uuid + "'.");
					log.severe(e.toString());
					continue;
				}
				
				if(null == rawData) {
					log.severe("No SOC data available for '" + uuid + "'.");
					continue;
				}
				
				for(BatterySocData socData : rawData) {
					// create data-link entries
					row = new DataLink4.Row();
					row.hypothesis = "REF"; // adapted from example file
					row.id = baseConfiguration.virtualSupplyPoint + "_" + batteryName + "_SOC";
					// truncate to minute as wished by seven2one
					row.date = socData.time.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.MINUTES);
					row.resolution = "min";
					row.unit = "%";
					row.value = socData.soc; // %
					dataLink.addRow(row);
				}
			}
			
			// upload data
			synchronized(sftp) {
				sftp.connect();
								
				pushData(configuration.path, baseConfiguration.virtualSupplyPoint, "history.csv", 
						dataLink.compile(configuration.separator, NumberFormat.getNumberInstance(Locale.forLanguageTag(configuration.numberFormatLanguageTag))),
						true);
								
				sftp.disconnect();
			}
		} catch(Exception e) {
			log.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	@Activate
	protected synchronized void activate() throws Exception {
		baseConfiguration = configurationService.get(BaseConfiguration.class);
		configuration = configurationService.get(UploaderConfiguration.class);
		
		sftp = new SftpConnection(configuration.host, configuration.port, configuration.user, configuration.password);
		
		/**
		 * Schedule a job for uploading data once a day at some time during the night
		 */
		task = timeService.schedule(this, (int)(Math.random() * 60), (int)(Math.random() * 60), configuration.uploadHour);		
		
		/*
		 * DEBUG
		 */
		if(configuration.debug == true) {
			log.info("Doing data upload for debugging purposes.");
			this.run();
		}
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
		task.cancel(false);
	}	
	
	@Reference(
			name = "TimeSeriesStorageService",
			service = TimeSeriesStorageService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeSeriesStorageService"
		)	
	protected synchronized void bindTimeSeriesStorageService(TimeSeriesStorageService dataStorageService) {
		timeSeriesStorageService = dataStorageService;
	}
	protected synchronized void unbindTimeSeriesStorageService(TimeSeriesStorageService dataStorageService) {
		timeSeriesStorageService = null;
	}

	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		this.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		this.configurationService = null;
	}
	
	@Reference(
			name = "TimeService",
			service = TimeService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeService"
		)	
	protected synchronized void bindTimeService(TimeService timeService) {
		this.timeService = timeService;
	}
	protected synchronized void unbindTimeService(TimeService timeService) {
		this.timeService = null;
	}
}
