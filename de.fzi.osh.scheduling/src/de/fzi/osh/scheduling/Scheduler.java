package de.fzi.osh.scheduling;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.com.control.ControlCommunicationService;
import de.fzi.osh.com.enabled.EnabledListenerService;
import de.fzi.osh.com.fms.FmsCommunicationService;
import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.core.component.OshComponent;
import de.fzi.osh.core.configuration.BaseConfiguration;
import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.core.data.Json;
import de.fzi.osh.forecasting.ForecastingService;
import de.fzi.osh.optimization.OptimizationService;
import de.fzi.osh.scheduling.communication.SchedulerEnabledListener;
import de.fzi.osh.scheduling.communication.SchedulerFmsListener;
import de.fzi.osh.scheduling.communication.SchedulerRemsListener;
import de.fzi.osh.scheduling.communication.SchedulerControlListener;
import de.fzi.osh.scheduling.configuration.SchedulerConfiguration;
import de.fzi.osh.scheduling.dataobjects.EnabledData;
import de.fzi.osh.scheduling.dataobjects.ScheduleMonitoringData;
import de.fzi.osh.scheduling.dataobjects.SchedulePublishingData;
import de.fzi.osh.scheduling.dataobjects.SchedulerData;
import de.fzi.osh.scheduling.dataobjects.TargetScheduleOptimizationData;
import de.fzi.osh.scheduling.jobs.RetrySchedulePublicationJob;
import de.fzi.osh.time.TimeService;
import de.fzi.osh.types.flexibilities.Flexibility;
import de.fzi.osh.types.flexibilities.Task;
import de.fzi.osh.wamp.configuration.WampConfiguration;

@Component(enabled=true,immediate=true)
public class Scheduler extends OshComponent<Scheduler, SchedulerConfiguration> {

	private static Logger log = Logger.getLogger(Scheduler.class.getName());
	
	// services
	private static TimeService timeService;
	private static List<OptimizationService> optimizationServices;
	private static List<ForecastingService> forecastingServices;
	private static ConfigurationService configurationService;
	private static EnabledListenerService enabledListenerService;
	private static FmsCommunicationService fmsCommunicationService;
	private static ControlCommunicationService controlCommunicationService;
	
	// configuration
	private static BaseConfiguration baseConfiguration;
	
	// communication
	private SchedulerCommunication bus;
	
	// optimization
	private SchedulerOptimizationQueue optimizationQueue;
	
	// scheduler data
	private volatile SchedulerData data;
	
	// temporary data
	// for keeping track of the adaptable flexibilities. Reason for employing a second map is the accessibility of the task id.
	private Map<UUID, Map<Integer, Task>> adaptableTasks;
	private Map<UUID, Map<Integer, Flexibility>> adaptableFlexibilities;
	
	/**
	 * Constructor
	 */
	public Scheduler() {
		// initialization
		controller = new SchedulerController(this);
		observer = new SchedulerObserver(this, controller);
		
		forecastingServices = new ArrayList<ForecastingService>();
		optimizationServices = new ArrayList<OptimizationService>();
		
		adaptableTasks = new HashMap<UUID, Map<Integer, Task>>();
		adaptableFlexibilities = new HashMap<UUID, Map<Integer, Flexibility>>();
	}
	
	/**
	 * Returns the base configuration.
	 * 
	 * @return
	 */
	public BaseConfiguration getBaseConfiguration() {
		return baseConfiguration;
	}
	
	/**
	 * Loads the scheduler data from a file
	 */
	private void restoreState(String filename) {
		data = Json.readFile(filename, SchedulerData.class);
	}
	
	/**
	 * Writes all data needed to persist into the persistence file
	 */
	public void saveState() {
		saveState(configuration.schedulerDataFile);
	}
	
	/**
	 * Writes the scheduler data to a file
	 */
	private synchronized void saveState(String filename) {
		Json.writeFile(filename, data);
	}
	
	/**
	 * Returns all scheduler data
	 * 
	 * @return
	 */
	public SchedulerData getData() {
		return data;
	}
	
	/**
	 * Updates the target schedule. 
	 * 
	 * @param schedule
	 */
	public synchronized void setTargetSchedule(PublicSchedule schedule) {
		if(data.targetSchedule == null) {
			data.targetSchedule = new TreeMap<Long, PublicSchedule>();
		}
		
		// remove old schedules
		for(Iterator<Map.Entry<Long, PublicSchedule>> iterator = data.targetSchedule.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<Long, PublicSchedule> entry = iterator.next();
			PublicSchedule scheduleEntry = entry.getValue();
			if(scheduleEntry.startingTime + scheduleEntry.consumption.length * scheduleEntry.slotLength < timeService.now()) {
				iterator.remove();
			}
		}
		
		data.targetSchedule.put(schedule.startingTime, schedule);
	}
	
	/**
	 * Returns the currently relevant target schedule.
	 * 
	 * @return
	 */
	public PublicSchedule getTargetSchedule() {
		if(data.targetSchedule == null) {
			return null;
		}

		long now = timeService.now();
		
		// remove old schedules
		for(Iterator<Map.Entry<Long, PublicSchedule>> iterator = data.targetSchedule.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<Long, PublicSchedule> entry = iterator.next();
			PublicSchedule scheduleEntry = entry.getValue();
			if(scheduleEntry.startingTime + scheduleEntry.consumption.length * scheduleEntry.slotLength < now) {
				iterator.remove();
			}
		}
		
		// find relevant schedule
		Map.Entry<Long, PublicSchedule> entry = data.targetSchedule.floorEntry(now);		
		return (null == entry ? null : entry.getValue());
	}
	
	/**
	 * Set data object to indicate an unfinished publication for an initial schedule. 
	 * 
	 * @param publishingData
	 */
	public synchronized void setIncompleteInitialPublication(SchedulePublishingData publishingData) {
		data.incompleteInitialPublication = publishingData;
	}
	
	/**
	 * Set data object to indicate an unfinished publication for a schedule update.
	 * 
	 * @param publishingData
	 */
	public synchronized void setIncompleteUpdatePublication(SchedulePublishingData publishingData) {
		data.incompleteUpdatePublication = publishingData;
	}
	
	/**
	 * Returns the list of all known adaptable tasks.
	 * 
	 * @return
	 */
	public Map<UUID, Map<Integer, Task>> getAdaptableTasks() {
		return adaptableTasks;
	}
	
	/**
	 * Returns the list of all known adaptable flexibilities.
	 * 
	 * @return
	 */
	public Map<UUID, Map<Integer, Flexibility>> getAdaptableFlexibilities() {
		return adaptableFlexibilities;
	}
	
	/**
	 * Returns the bus connection.
	 * 
	 * @return
	 */
	public SchedulerCommunication getCommunicationInterface() {
		return bus;
	}
	
	/**
	 * Returns the optimization queue.
	 * 
	 * @return
	 */
	public SchedulerOptimizationQueue getOptimizationQueue() {
		return optimizationQueue;
	}
	
	/**
	 * Returns all known forecasting services.
	 * 
	 * @return
	 */
	public List<ForecastingService> getForecastingServices() {
		return forecastingServices;
	}
	
	/**
	 * Returns all known optimization services.
	 * 
	 * @return
	 */
	public List<OptimizationService> getOptimizationServices() {
		return optimizationServices;
	}
	
	/**
	 * Returns the fms communication service.
	 * 
	 * @return
	 */
	public static FmsCommunicationService getFmsCommunicationService() {
		return fmsCommunicationService;
	}
	
	/**
	 * Returns the control communication service.
	 * 
	 * @return
	 */
	public static ControlCommunicationService getControlCommunicationService() {
		return controlCommunicationService;
	}
	
	/**
	 * Returns the time service.
	 * 
	 * @return
	 */
	public static TimeService getTimeService() {
		return timeService;
	}
	
	@Override
	public void run() {		
		log.log(Level.INFO, "Starting Scheduler");		

		// load configuration
		baseConfiguration = configurationService.get(BaseConfiguration.class);
		configuration = configurationService.get(SchedulerConfiguration.class);
		WampConfiguration wampConfiguration = configurationService.get(WampConfiguration.class);
		
		observer.initialize();
		controller.initialize();
		
		try {
			timeService.sleep(configuration.startupDelay);
		} catch (InterruptedException e1) {
		}
		
		// start up process
		// restore last state
		restoreState(configuration.schedulerDataFile);
		
		// clean data
		if(data.incompleteInitialPublication != null && data.incompleteInitialPublication.to < timeService.now()) {
			data.incompleteInitialPublication = null;
		}
		if(data.incompleteUpdatePublication != null && data.incompleteUpdatePublication.to < timeService.now()) {
			data.incompleteUpdatePublication = null;
		}
		
		// connect to bus
		bus = new SchedulerCommunication(this, wampConfiguration);
		bus.open();
		bus.waitForConnection();

		// optimization queue
		optimizationQueue = new SchedulerOptimizationQueue(this);
		
		// schedule a job for publishing schedules to fms	
		{
			log.info("Scheduling fms publishing cron job.");
			
			String[] parameters = configuration.fmsSchedulePublishingCronExpression.split(" ");
			Integer second;
			Integer minute;
			Integer hour;
			if(parameters.length != 3) {
				log.severe("Could not parse fms cron expression. Using 09:00:00 as schedule publishing time.");
				second = 0;
				minute = 0;
				hour = 9;
			} else {
				// parse second value
				try {
					if(parameters[0].equals("*")) {
						second = null;
					} else {
						second = Integer.parseInt(parameters[0]);
					}
				} catch(Exception e) {
					log.severe(e.toString());
					log.info("Setting second = null.");
					second = null;
				}
				// parse minute value
				try {
					if(parameters[1].equals("*")) {
						minute = null;
					} else {
						minute = Integer.parseInt(parameters[1]);
					}
				} catch(Exception e) {
					log.severe(e.toString());
					log.info("Setting second = null.");
					minute = null;
				}
				// parse hour value
				try {
					if(parameters[2].equals("*")) {
						hour = null;
					} else {
						hour = Integer.parseInt(parameters[2]);
					}
				} catch(Exception e) {
					log.severe(e.toString());
					log.info("Setting second = null.");
					hour = null;
				}
			}
			
			// actual scheduling
			timeService.schedule(new Runnable() {	
				@Override
				public void run() {
			        // create data object
			        SchedulePublishingData data = new SchedulePublishingData();
			        
			        ZonedDateTime from = Scheduler.getTimeService().nowAsZonedDateTime().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
			        data.from = from.toEpochSecond();
			        data.to = from.plus(1, ChronoUnit.DAYS).toEpochSecond();
			        data.initial = true;
			        
			        optimizationQueue.queueSchedulePublication(data);
				}
			}, second, minute, hour);
		}
		
		
		/**
		 * DEBUG
		 *
		{
	        // create data object
	        SchedulePublishingData data = new SchedulePublishingData();				        
	        Instant now = timeService.nowAsInstant();
	        data.from = now.getEpochSecond();
	        data.to = now.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS).toEpochSecond();
	        data.initial = false;
	        // queue publication
		    this.getOptimizationQueue().queueSchedulePublication(data);
		}
	    /**
	     * END DEBUG
	     */
		
		// do initial schedule publication if overdue
		try {
			if(ZonedDateTime.ofInstant(Instant.ofEpochSecond(data.latestInitialSchedulePublication), ZoneId.systemDefault()).plusDays(1)
					.isBefore(timeService.nowAsZonedDateTime())) {

				log.info("Overdue schedule publication.");
				
				// start a new attempt to publish the schedule
				data.incompleteInitialPublication = null;
				SchedulePublishingData publishingData = new SchedulePublishingData();
		        
		        ZonedDateTime from = ZonedDateTime.ofInstant(Instant.ofEpochSecond(data.latestInitialSchedulePublication), ZoneId.systemDefault()).
		        		truncatedTo(ChronoUnit.DAYS).plus(2, ChronoUnit.DAYS); // add 2 days, since $targetTime is 1 in the past
		        publishingData.from = from.toEpochSecond();
		        publishingData.to = from.plus(1, ChronoUnit.DAYS).toEpochSecond();
		        
		        // has the scheduler stopped working for more than a day?
		        if(publishingData.to < timeService.now()) {
		        	// update values to publish a schedule for today
			        publishingData.from = timeService.now();
			        publishingData.to = timeService.nowAsZonedDateTime().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS).toEpochSecond();
		        }				        
		        publishingData.initial = true;
		        
		        optimizationQueue.queueSchedulePublication(publishingData);
			}
		} catch(Exception e) {
			log.severe("Making up on initial schedule publication failed.");
			e.printStackTrace();
			log.severe(e.toString());
		}
		
		// Schedule a job for retrying schedule publications
		timeService.scheduleAtRate(new RetrySchedulePublicationJob(this), 0, configuration.fmsFailureWaitingTime * 1000);
		
		// at startup and the begin of every new day do a target schedule optimization and thereby poll all flexibilities
		timeService.schedule(new Runnable() {
			@Override
			public void run() {
				optimizationQueue.queueTargetScheduleOptimization(new TargetScheduleOptimizationData(), true);
			}}, 1, 0, 0); // every day  at 00:00:01
		optimizationQueue.queueTargetScheduleOptimization(new TargetScheduleOptimizationData(), false);		
		
		// schedule a job for flexibility adaptation
		timeService.scheduleAtRate(new Runnable() {			
			@Override
			public void run() {
				try {
					// if disabled by GCU or VNB (=SOC target), there is nothing to adjust
					if(data.GCUEnabled && (null == data.targetChargeData || data.targetChargeData.time <= timeService.now()) && data.targetSchedule != null) {
						// start flexibility adaption
						observer.update(new ScheduleMonitoringData());
					}
				} catch(Exception e) {
					// don't quit if there goes anything wrong!
					e.printStackTrace();
					log.severe(e.toString());
				}	
			}
		}, 0, configuration.scheduleAdpationInterval);
		
		if(baseConfiguration.batteryUUIDs.length > 0) {
			// schedule a job for providing battery data to the REMS
			timeService.scheduleAtRate(new Runnable() {
				@Override
				public void run() {
					try {						
						for(UUID uuid: baseConfiguration.batteryUUIDs) {
							// since there is only one battery per bems in grid-control this is sufficient 
							bus.getBatteryState(uuid, response -> {
								Map<Short, Short> inputRegisters = new HashMap<Short, Short>();

								// soc
								inputRegisters.put((short) 125, (short)response.stateOfCharge);
								
								// system state
								inputRegisters.put((short)200, response.systemStateCode);
								
								// system error codes
								inputRegisters.put((short)235, response.systemErrorCode[0]);
								inputRegisters.put((short)236, response.systemErrorCode[1]);
								inputRegisters.put((short)237, response.systemErrorCode[2]);
								inputRegisters.put((short)238, response.systemErrorCode[3]);
								
								// debug output for error code != 0
								if(response.systemErrorCode[0] != 0 || response.systemErrorCode[1] != 0 ||
										response.systemErrorCode[2] != 0 ||	response.systemErrorCode[3] != 0) {
									log.warning("Battery system error code: [" + 
										response.systemErrorCode[0] + ", " + 
										response.systemErrorCode[1] + ", " +
										response.systemErrorCode[2] + ", " +
										response.systemErrorCode[3] + "]");
								}

								enabledListenerService.publishData(null, null, inputRegisters, null);
							});
						}

						// enabled signal
						boolean enabled = enabledListenerService.isEnabled();
						bus.setEnabled(enabled);
					} catch(Exception e) {
						e.printStackTrace();
						log.severe("Provision of data to REMS failed. [" + e.toString() + "]");
					}
				}			
			}, configuration.remsDataProvisionInterval, configuration.remsDataProvisionInterval);
		}
		
		
		// propagating enabled signal
		try {
			EnabledData enabledData = new EnabledData();
			enabledData.enabled = data.GCUEnabled;
			observer.update(enabledData);
			
			enabledListenerService.setEnabled(data.GCUEnabled);
		} catch(Exception e) {
			log.severe("Could not set enabled signal");
			log.severe(e.toString());
		}
		
		log.info("Scheduler initialized.");
	}
	
	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		Scheduler.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		Scheduler.configurationService = null;
	}
	
	@Reference(
			name = "ForecastingService",
			service = ForecastingService.class,
			cardinality = ReferenceCardinality.AT_LEAST_ONE,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindForecastingService"
		)	
	protected synchronized void bindForecastingService(ForecastingService forecastingService) {
		Scheduler.forecastingServices.add(forecastingService);
	}
	protected synchronized void unbindForecastingService(ForecastingService forecastingService) {
		Scheduler.forecastingServices.remove(forecastingService);
	}
	
	@Reference(
			name = "OptimizationService",
			service = OptimizationService.class,
			cardinality = ReferenceCardinality.AT_LEAST_ONE,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindOptimizationService"
		)	
	protected synchronized void bindOptimizationService(OptimizationService optimizationService) {
		Scheduler.optimizationServices.add(optimizationService);
	}
	protected synchronized void unbindOptimizationService(OptimizationService optimizationService) {
		Scheduler.optimizationServices.remove(optimizationService);
	}
	
	@Reference(
			name = "FmsCommunicationService",
			service = FmsCommunicationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindFmsCommunicationService"
		)	
	protected synchronized void bindFmsCommunicationService(FmsCommunicationService fmsCommunicationService) {
		Scheduler.fmsCommunicationService = fmsCommunicationService;
		
		// setup an fms listener
		Scheduler.fmsCommunicationService.addListener(new SchedulerFmsListener(this));
	}
	protected synchronized void unbindFmsCommunicationService(FmsCommunicationService fmsCommunicationService) {
		Scheduler.fmsCommunicationService = null;
	}
	
	@Reference(
			name = "ControlCommunicationService",
			service = ControlCommunicationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindVnbCommunicationService"
		)	
	protected synchronized void bindVnbCommunicationService(ControlCommunicationService vnbCommunicationService) {
		Scheduler.controlCommunicationService = vnbCommunicationService;
		
		// setup an fms listener
		Scheduler.controlCommunicationService.addListener(new SchedulerControlListener(this));
	}
	protected synchronized void unbindVnbCommunicationService(ControlCommunicationService vnbCommunicationService) {
		Scheduler.controlCommunicationService = null;
	}
	
	@Reference(
			name = "EnabledListenerService",
			service = EnabledListenerService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindEnabledListenerService"
		)	
	protected synchronized void bindEnabledListenerService(EnabledListenerService enabledListenerService) {
		Scheduler.enabledListenerService = enabledListenerService;
		
		// setup an enabled listener
		Scheduler.enabledListenerService.addListener(new SchedulerEnabledListener(this));
		Scheduler.enabledListenerService.addDataListener(new SchedulerRemsListener(this));
	}
	protected synchronized void unbindEnabledListenerService(EnabledListenerService enabledListenerService) {
		Scheduler.enabledListenerService = null;
	}
	
	@Reference(
			name = "TimeService",
			service = TimeService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeService"
		)	
	protected synchronized void bindTimeService(TimeService timeService) {
		Scheduler.timeService = timeService;
	}
	protected synchronized void unbindTimeService(TimeService timeService) {
		Scheduler.timeService = null;
	}

	@Activate
	protected synchronized void activate() throws Exception {		
		Thread thread = new Thread(this);
		thread.start();
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
		log.info("Shutting down scheduler.");
	}
	
}
