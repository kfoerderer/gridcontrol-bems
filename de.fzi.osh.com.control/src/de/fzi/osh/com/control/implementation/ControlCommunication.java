package de.fzi.osh.com.control.implementation;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import com.jcraft.jsch.SftpATTRS;

import de.fzi.osh.com.control.ControlCommunicationListener;
import de.fzi.osh.com.control.ControlCommunicationService;
import de.fzi.osh.com.control.configuration.ControlCommunicationConfiguration;
import de.fzi.osh.core.configuration.BaseConfiguration;
import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.core.data.SftpConnection;
import de.fzi.osh.time.TimeService;

@Component(service=ControlCommunicationService.class, immediate=true)
public class ControlCommunication implements ControlCommunicationService, Runnable {
	
	private static Logger log = Logger.getLogger(ControlCommunication.class.getName());

	private List<ControlCommunicationListener> listeners;
	private static ConfigurationService configurationService;	
	private static BaseConfiguration baseConfiguration;
	private static ControlCommunicationConfiguration configuration;
	
	private SftpConnection sftp;
	
	public ControlCommunication() {
		listeners = new ArrayList<ControlCommunicationListener>();
	}
	
	@Override
	public void addListener(ControlCommunicationListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(ControlCommunicationListener listener) {
		listeners.remove(listener);
	}	
		
	/**
	 * Pushes a file to the communication platform
	 * 
	 * @param basePath GEMS<->VNB communication directory. Assumed to be existing.
	 * @param folder Folder for this specific GEMS. Created if not existing.  
	 * @param filename Name of file the data is written to.
	 * @param data The actual data to be written.
	 * @oaram backup Upload a copy of the file as backup
	 * @param data
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
			log.finest("Polling control interface. [" + configuration.path + "/" + baseConfiguration.virtualSupplyPoint + "]");
			
			// Look if there are new commands
			synchronized(sftp) {
				sftp.connect();
				
				if(false == sftp.changeDirectory(configuration.path + "/" + baseConfiguration.virtualSupplyPoint)) {
					log.warning("Could not enter directory '" + configuration.path + "/" + baseConfiguration.virtualSupplyPoint + "'.");
					// try to create the output folder, if it is not existing yet
					log.info("Creating folder '" + configuration.path + "/" + baseConfiguration.virtualSupplyPoint + "'");
					sftp.makeDirectory(configuration.path + "/" + baseConfiguration.virtualSupplyPoint);
					if(false == sftp.changeDirectory(configuration.path + "/" + baseConfiguration.virtualSupplyPoint)) {
						throw new Exception("Could not enter folder'" + configuration.path + "/" + baseConfiguration.virtualSupplyPoint + "'.");
					}
				}
				
				// timestamp which is added to the file names
				String timestamp = timeService.nowAsInstant().toString().replaceAll(":", "-");
				
				SftpATTRS attributes = sftp.getAttributes(PublicationType.Execute.getFilename());				
				if(null != attributes) {
					log.info("New commands available." );
					// new commands
					String content = sftp.read(PublicationType.Execute.getFilename());

					// parse content
					Scanner scanner = new Scanner(content);
					while (scanner.hasNextLine()) {
						String line = scanner.nextLine();
					  
						Pattern pattern = Pattern.compile("^battery\\s-(\\S+)\\s(\\S+)\\s(\\S+)");
						Matcher matcher = pattern.matcher(line);
						
						// battery command
						if(matcher.matches()) {
							
							String type = matcher.group(1);
							String value = matcher.group(2);
							String time = matcher.group(3);
							
							try {
								if(type.equalsIgnoreCase("soc")) {
									log.info("Setting target soc.");
									// notify listeners [before renaming => file isn't marked as read]
									for(ControlCommunicationListener listener : listeners) {
										listener.setTargetSOC(Integer.parseInt(value), Long.parseLong(time));
									}
								} else if(type.equalsIgnoreCase("wh")) {
									log.info("Setting targte wh.");
									// notify listeners [before renaming => file isn't marked as read]
									for(ControlCommunicationListener listener : listeners) {
										listener.setTargetWh(Integer.parseInt(value), Long.parseLong(time));
									}
								}
							} catch(Exception e) {
								log.severe("Executing battery command failed.");
							}
						}
					}
					scanner.close();

					log.finest("Renaming file.");
					// add timestamp to filename
					sftp.rename(PublicationType.Execute.getFilename(), timestamp + "_" + PublicationType.Execute.getFilename());
				}
				
				sftp.disconnect();
			}
		} catch(Exception e) {
			log.severe(e.getMessage());
		}
	}
	
	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		ControlCommunication.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		ControlCommunication.configurationService = null;
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
		ControlCommunication.timeService = timeService;
	}
	protected synchronized void unbindTimeService(TimeService timeService) {
		ControlCommunication.timeService = null;
	}
	
	@Activate
	protected synchronized void activate() throws Exception {
		baseConfiguration = configurationService.get(BaseConfiguration.class);
		configuration = configurationService.get(ControlCommunicationConfiguration.class);
		
		sftp = new SftpConnection(configuration.host, configuration.port, configuration.user, configuration.password);
		
		timeService.scheduleAtRate(this, 0, configuration.pollingPeriod * 1000);
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
	}

	@Override
	public void publishMessage(String message) {
		try {
			// Look if there are new commands
			synchronized(sftp) {
				sftp.connect();
				
				// timestamp which is added to the file names
				String timestamp = timeService.nowAsInstant().toString().replaceAll(":", "-");
				
				pushData(configuration.path, baseConfiguration.virtualSupplyPoint, timestamp + "_message", message, false);
				
				sftp.disconnect();
			}
		} catch(Exception e) {
			log.severe(e.getMessage());
		}
	}
}
