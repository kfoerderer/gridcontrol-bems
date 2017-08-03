package de.fzi.osh.scheduling;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

import de.fzi.osh.com.fms.FmsCommunicationService.PublicationType;
import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.core.configuration.BaseConfiguration;
import de.fzi.osh.core.oc.Controller;
import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.core.timeseries.IntegerTimeSlotTransformer;
import de.fzi.osh.core.timeseries.TimeSeries;
import de.fzi.osh.forecasting.ForecastingService;
import de.fzi.osh.forecasting.demand.ElectricityDemandForecast;
import de.fzi.osh.forecasting.solar.SolarPowerForecast;
import de.fzi.osh.optimization.OptimizationService;
import de.fzi.osh.optimization.schedule.SchedulingProblem;
import de.fzi.osh.optimization.schedule.SchedulingSolution;
import de.fzi.osh.optimization.schedule.TargetScheduleProblem;
import de.fzi.osh.optimization.schedule.TargetScheduleSolution;
import de.fzi.osh.scheduling.configuration.SchedulerConfiguration;
import de.fzi.osh.scheduling.dataobjects.EnabledData;
import de.fzi.osh.scheduling.dataobjects.FlexibilitiesChangedData;
import de.fzi.osh.scheduling.dataobjects.TargetScheduleData;
import de.fzi.osh.scheduling.dataobjects.ScheduleMonitoringData;
import de.fzi.osh.scheduling.dataobjects.SchedulePublishingData;
import de.fzi.osh.scheduling.dataobjects.TargetBatteryChargeData;
import de.fzi.osh.scheduling.dataobjects.TargetScheduleOptimizationData;
import de.fzi.osh.time.TimeService;
import de.fzi.osh.types.flexibilities.Flexibility;
import de.fzi.osh.types.flexibilities.SchedulingResult;
import de.fzi.osh.types.flexibilities.Task;
import de.fzi.osh.types.math.IntInterval;

public class SchedulerController extends Controller<Scheduler, SchedulerConfiguration> {

	private static Logger log = Logger.getLogger(SchedulerController.class.getName());
		
	private BaseConfiguration baseConfiguration;
	private static TimeService timeService;
	
	private Object optimizationLock;
		
	public SchedulerController(Scheduler component) {
		super(component);
	}
	
	@Override
	public void initialize() {
		configuration = component.getConfiguration();
		baseConfiguration = component.getBaseConfiguration();
		
		timeService = Scheduler.getTimeService();
		
		optimizationLock = new Object();
	}
	
	@Override
	public void update(DataObject data) {		
		// enabled signal
		if(data instanceof EnabledData) {
			setEnabled((EnabledData) data);
		}
		// new schedule
		else if(data instanceof TargetScheduleData) {
			TargetScheduleData scheduleData = (TargetScheduleData) data;
			// set the new schedule
			// invalid schedules are filtered by now
			component.setTargetSchedule(scheduleData.schedule);
			// persistence
			component.saveState();
			// start target schedule optimization
			component.getOptimizationQueue().queueTargetScheduleOptimization(new TargetScheduleOptimizationData(), true);
		}
		// flexibility adaption
		else if(data instanceof ScheduleMonitoringData) {
			// fast response needed, no thread locking or sleeping in here!
			adaptFlexibilities((ScheduleMonitoringData) data);	
		}
		// the set of available flexibilities has changed
		else if(data instanceof FlexibilitiesChangedData) {
			// fast response needed, no thread locking or sleeping in here!
			component.getOptimizationQueue().queueTargetScheduleOptimization(new TargetScheduleOptimizationData(), false);
		}
		// a target schedule optimization has to be performed
		else if(data instanceof TargetScheduleOptimizationData) {
			// only one optimization at a time
			synchronized (optimizationLock) {
				optimizeTargetScheduleCompliance((TargetScheduleOptimizationData) data);
			}
		}
		// publish a schedule for the following day
		else if(data instanceof SchedulePublishingData) {
			// only one optimization at a time
			synchronized (optimizationLock) {
				publishSchedule((SchedulePublishingData) data);
			}
		}
		// vnb communication
		else if(data instanceof TargetBatteryChargeData) {
			setBatteryChargeTarget((TargetBatteryChargeData) data);
		}
	}
	
	/**
	 * Enabled signal.
	 * 
	 * @param enabledData
	 */
	private void setEnabled(EnabledData enabledData) {
		log.fine("Received enabled signal '" + enabledData.enabled + "'.");
	
		// keep track of state
		component.getData().GCUEnabled = enabledData.enabled;
		
		component.getCommunicationInterface().setEnabled(enabledData.enabled);	
		
		// may not be needed, if GCU sends enabled periodically
		component.saveState();
	}
	
	/**
	 * Handles vnb commands for battery charge
	 * 
	 * @param targetChargeData
	 */
	private void setBatteryChargeTarget(TargetBatteryChargeData chargeData) {
		log.fine("Received battery charge command from VNB.");
		
		component.getData().targetChargeData = chargeData;
		component.saveState();
		
		for(UUID uuid : baseConfiguration.batteryUUIDs) {
			component.getCommunicationInterface().setBatteryTarget(uuid, chargeData.soc, chargeData.time);
		}
	}
	
	/**
	 * Adapt flexibilities
	 * 
	 * [!] time critical, do not lock this thread
	 * 
	 * @param adaptationData
	 */
	private void adaptFlexibilities(ScheduleMonitoringData adaptationData) {
		// calculate deviation
		// difference > 0 = higher consumption needed
		double difference = adaptationData.schedule - (adaptationData.consumption + adaptationData.battery + adaptationData.production);
		
		// There can only be one running flexibility per device
		Map<UUID, Task> currentlyRunning = new HashMap<UUID, Task>();
		
		// DEBUG
		System.out.println(difference + "\t=" + adaptationData.schedule + "\t- ( " + adaptationData.consumption + "\t+" + adaptationData.production + "\t+" + adaptationData.battery + ")");
		
		// get all currently running adaptable flexibilities
		long now = timeService.now();
		for(Iterator<Map.Entry<UUID, Map<Integer, Task>>> deviceIterator = component.getAdaptableTasks().entrySet().iterator(); deviceIterator.hasNext();) {
			Map.Entry<UUID, Map<Integer, Task>> deviceEntry = deviceIterator.next();
			
			Map<Integer, Task> taskMap = deviceEntry.getValue();
			for(Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) { 
				//is it running right now?
				Task task = taskEntry.getValue();
				if(task.startingTime <= now && task.startingTime + task.runningTime > now) {
					currentlyRunning.put(deviceEntry.getKey(), task);
				}
			}
		}
		
		// adapt 
		for(Map.Entry<UUID, Task> entry : currentlyRunning.entrySet()) {
			Task adaptableTask = entry.getValue();
			
			Flexibility flexibility = null;
			Map<Integer, Flexibility> flexibilityMap = component.getAdaptableFlexibilities().get(entry.getKey());
			if(flexibilityMap != null) {
				flexibility = flexibilityMap.get(adaptableTask.flexibilityId);
			} 
			if(null == flexibility) {
				log.warning("Missing flexibility data for adaptation.");
				return;
			}
			
			// backup the task data
			NavigableMap<Integer, Integer> powerDataBackup = new TreeMap<Integer, Integer>();
			powerDataBackup.putAll(adaptableTask.power);
			
			// get relevant schedule data
			int relativeNow = (int)(now - adaptableTask.startingTime); // > 0 , else it would not be in currentlyRunning
			NavigableMap<Integer, Integer> relevantPowers = adaptableTask.power.subMap(relativeNow, true,	relativeNow + configuration.flexibilityAdaptionHorizon, true);
			// Overwrite the whole window. Just adding a delta can easily lead to feedback loops.
			// TODO: better adaptation logic (some time in the future)
			Iterator<Map.Entry<Integer, Integer>> iterator = relevantPowers.entrySet().iterator();
			while(iterator.hasNext()) {
				iterator.next();
				iterator.remove();
			}
			// target = current + delta
			Integer current = adaptableTask.power.floorEntry(relativeNow).getValue();
			int targetPower = (int)((current == null ? 0 : current) + difference / currentlyRunning.size()); // difference > 0 => consume more energy
			IntInterval powerConstraint = flexibility.powerCorridor.floorEntry(relativeNow).getValue();
			targetPower = Math.min(targetPower, powerConstraint.max);
			targetPower = Math.max(targetPower, powerConstraint.min);
			IntInterval energyConstraint = flexibility.getEnergyConstraint(adaptableTask.power, relativeNow);
			if(energyConstraint.max <= targetPower * configuration.flexibilityAdaptionHorizon && targetPower > 0) {
				// DEBUG
				System.out.println(energyConstraint.max + " <= " + targetPower + " * " + configuration.flexibilityAdaptionHorizon);
				targetPower = 0;
			} else if(energyConstraint.min >= targetPower * configuration.flexibilityAdaptionHorizon && targetPower < 0) {
				// DEBUG
				System.out.println(energyConstraint.min + " >= " + targetPower + " * " + configuration.flexibilityAdaptionHorizon);
				targetPower = 0;
			}
			else {
				// DEBUG
				System.out.println("Target: " + targetPower);
			}
			relevantPowers.put(relativeNow, targetPower);
			// make sure only values within adaption horizon are changed
			relevantPowers.put(relativeNow + configuration.flexibilityAdaptionHorizon, powerDataBackup.floorEntry(relativeNow + configuration.flexibilityAdaptionHorizon).getValue());
			
			if(flexibility.checkValidity(adaptableTask.startingTime, adaptableTask.power)) {
				// do adaptation
				component.getCommunicationInterface().adaptFlexibility(entry.getKey(), adaptableTask.flexibilityId, relevantPowers,
						response -> {
							if(response.result != SchedulingResult.Ok) {
								log.info("Schedule adaption not possible for application '" + entry.getKey() + "' (" + response.result + ").");
								
								// restore power data
								adaptableTask.power = powerDataBackup;
								
								// only start a new thread if it is really necessary			
								if(timeService.now() > component.getData().mostRecentTargetScheduleOptimization + configuration.minimumComplianceOptimizationInterval) {
									component.getOptimizationQueue().queueTargetScheduleOptimization(new TargetScheduleOptimizationData(), false);
								}
							}
						}, error -> {
							log.info("Schedule adaption not possible for application '" + entry.getKey() + "' (" + error.toString() + ").");
							
							// restore power data
							adaptableTask.power = powerDataBackup;
										
							// only start a new thread if it is really necessary			
							if(timeService.now() > component.getData().mostRecentTargetScheduleOptimization + configuration.minimumComplianceOptimizationInterval) {
								component.getOptimizationQueue().queueTargetScheduleOptimization(new TargetScheduleOptimizationData(), false);
							}
						}, null);
			} else {
				log.info("Schedule adaption not valid for application '" + entry.getKey() + "'.");
				// restore power data
				adaptableTask.power = powerDataBackup;
				// only start a new thread if it is really necessary			
				if(timeService.now() > component.getData().mostRecentTargetScheduleOptimization + configuration.minimumComplianceOptimizationInterval) {
					component.getOptimizationQueue().queueTargetScheduleOptimization(new TargetScheduleOptimizationData(), false);
				}
			}
		}
	}
	
	
	/**
	 * Returns the start of the current time slot as zoned date time. Assuming that an hours divides into an integer amount of slots.
	 * 
	 * @return
	 */
	private ZonedDateTime getCurrentTimeSlotStart() {
		ZonedDateTime now = timeService.nowAsZonedDateTime();
		ZonedDateTime hour = now.truncatedTo(ChronoUnit.HOURS);
		
		// how many seconds have passed since the current hour started?
		int secondsIntoHour = now.getMinute() * 60 + now.getSecond();
		// now floor to current slot begin
		secondsIntoHour -= secondsIntoHour % configuration.scheduleOptimizationSlotLength;
		
		return hour.plus(secondsIntoHour, ChronoUnit.SECONDS);
	}
	
	/**
	 * Makes sure the given time is the begin of a slot. If the time doesn't match a begin it is ceiled to the next begin.
	 * 
	 * @return
	 */
	private ZonedDateTime ensureTimeIsAtSlotStart(ZonedDateTime time) {
		ZonedDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
		
		// how many seconds have passed since the current hour started?
		int secondsIntoHour = time.getMinute() * 60 + time.getSecond();
		// now floor to current slot begin
		secondsIntoHour -= secondsIntoHour % configuration.scheduleOptimizationSlotLength;
		hour = hour.plus(secondsIntoHour, ChronoUnit.SECONDS);
		
		if(hour.toEpochSecond() != time.toEpochSecond()) {
			// time is in between slot
			return hour.plus(configuration.scheduleOptimizationSlotLength, ChronoUnit.SECONDS);
		}		
		return hour;
	}
	
	/**
	 * Returns the expected electricity demand during the given time frame using the first fitting forecasting service.
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	private int[] getConsumptionForecast(long from, long to) {
		// if there is no consumption, there is no need to forecast anything
		if(baseConfiguration.consumptionMeterUUIDs.length == 0) {
			return new int[(int)((to - from) / configuration.scheduleOptimizationSlotLength)];
		}
		
		int[] electricityDemand;
		for(ForecastingService service : component.getForecastingServices()) {
			if(service.canForecast(ElectricityDemandForecast.class)) {
				ElectricityDemandForecast demand = service.getForecast(
						ZonedDateTime.ofInstant(Instant.ofEpochSecond(from), ZoneId.systemDefault()),
						ZonedDateTime.ofInstant(Instant.ofEpochSecond(to), ZoneId.systemDefault()), ElectricityDemandForecast.class, null);
				
				// convert to time series
				TimeSeries<Integer> demandSeries = demand.getTimeSeries();
				// adapt slot length if necessary
				if(demand.timeSlotLength != configuration.scheduleOptimizationSlotLength) {
					log.info("Time slot length of electricity demand forecast (" + demand.timeSlotLength + 
							") and scheduling input (" + configuration.scheduleOptimizationSlotLength + ") do not match. Doing adaptation.");
					
					IntegerTimeSlotTransformer transformer = new IntegerTimeSlotTransformer(demand.forecastBegin.toInstant().getEpochSecond(),
							demand.forecastEnd.toInstant().getEpochSecond(), configuration.scheduleOptimizationSlotLength);					
					demandSeries = transformer.transform(demandSeries);
				}
				electricityDemand = demandSeries.getValues().values().stream().mapToInt(i -> i).toArray();
				// convert W to Wh
				for(int i = 0; i < electricityDemand.length; i++) {
					electricityDemand[i] *= configuration.scheduleOptimizationSlotLength / 3600.0;
				}
				return electricityDemand;
			}
		}
		return null;
	}
	
	/**
	 * Returns the expected electricity production in during the given time frame using the first fitting forecasting service.
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	private int[] getProductionForecast(long from, long to) {
		// if there is no production, there is no need to forecast anything
		if(baseConfiguration.productionMeterUUIDs.length == 0) {
			return new int[(int)((to - from) / configuration.scheduleOptimizationSlotLength)];
		}
		
		int[] electricityProduction = null;
		// get forecasts, use first suitable service
		for(ForecastingService service : component.getForecastingServices()) {
			if(service.canForecast(SolarPowerForecast.class)) {
				for(UUID meterUUID : baseConfiguration.productionMeterUUIDs) {
					SolarPowerForecast power = service.getForecast(
							ZonedDateTime.ofInstant(Instant.ofEpochSecond(from), ZoneId.systemDefault()),
							ZonedDateTime.ofInstant(Instant.ofEpochSecond(to), ZoneId.systemDefault()), SolarPowerForecast.class, meterUUID);
					
					// convert to time series
					TimeSeries<Integer> powerSeries = power.getTimeSeries();
					// adapt slot length if necessary
					if(power.timeSlotLength != configuration.scheduleOptimizationSlotLength) {
						log.info("Time slot length of electricity demand forecast (" + power.timeSlotLength + 
								") and scheduling input (" + configuration.scheduleOptimizationSlotLength + ") do not match. Doing adaptation.");
					
						IntegerTimeSlotTransformer transformer = new IntegerTimeSlotTransformer(power.forecastBegin.toInstant().getEpochSecond(),
								power.forecastEnd.toInstant().getEpochSecond(), configuration.scheduleOptimizationSlotLength);					
						powerSeries = transformer.transform(powerSeries);
					}
					
					if(null == electricityProduction) {
						// initial run
						electricityProduction = powerSeries.getValues().values().stream().mapToInt(i -> i).toArray();
					} else {
						// add to previous forecasts
						// both arrays should be of same length
						int[] powers = powerSeries.getValues().values().stream().mapToInt(i -> i).toArray();
						for(int i = 0; i < electricityProduction.length; i++) {
							electricityProduction[i] += powers[i];
						}
					}
				}
				// convert W to Wh
				for(int i = 0; i < electricityProduction.length; i++) {
					electricityProduction[i] *= configuration.scheduleOptimizationSlotLength / 3600.0;
				}
				
				return electricityProduction;
			}
		}
		return null;
	}
		
	/**
	 * Start an optimization to follow the target schedule as close as possible.
	 * 
	 * @param optimizationData
	 */
	private void optimizeTargetScheduleCompliance(TargetScheduleOptimizationData optimizationData){		
				
		// collect problem data
		TargetScheduleProblem problem = new TargetScheduleProblem();
		
		problem.from = timeService.nowAsZonedDateTime().plus(configuration.optimizationTimeBuffer, ChronoUnit.SECONDS).toEpochSecond();
		problem.currentSlotBegin = getCurrentTimeSlotStart().toEpochSecond();		

		problem.optimizationTimeBuffer = configuration.optimizationTimeBuffer;
		problem.flexibilityAdaptionBuffer = configuration.flexibilityAdaptationBuffer;
		
		// deal with missing target schedules by setting target = 0
		boolean missingTargetSchedule = false;
		PublicSchedule targetSchedule = component.getTargetSchedule();
		if(targetSchedule == null) {
			log.warning("No target schedule for optimization found.");
			log.info("Optimizing self consumption, i.e. target = 0.");
			
			missingTargetSchedule = true;
			
			// create target schedule with consumption equal 0
			targetSchedule = new PublicSchedule();
			targetSchedule.startingTime = problem.currentSlotBegin;
			targetSchedule.slotLength = configuration.scheduleOptimizationSlotLength;
			// to end of day
			long to = ZonedDateTime.ofInstant(Instant.ofEpochSecond(targetSchedule.startingTime), ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS).plusDays(1).toEpochSecond();
			targetSchedule.consumption = new int[(int)((to - problem.currentSlotBegin) / configuration.scheduleOptimizationSlotLength)];
			targetSchedule.production =  new int[targetSchedule.consumption.length];
			targetSchedule.flexibleConsumption = new int[targetSchedule.consumption.length];
			targetSchedule.flexibleProduction = new int[targetSchedule.consumption.length];
		}
		problem.targetSchedule = targetSchedule;
		
		ZonedDateTime startingTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(targetSchedule.startingTime), ZoneId.systemDefault());
		problem.to = startingTime.plus(targetSchedule.slotLength * targetSchedule.consumption.length, ChronoUnit.SECONDS).toEpochSecond();
		problem.slotLength = configuration.scheduleOptimizationSlotLength;
	
		// get forecasts
		problem.electricityDemand = getConsumptionForecast(problem.currentSlotBegin, problem.to);
		problem.electricityProduction = getProductionForecast(problem.currentSlotBegin, problem.to);
		
		// get all available flexibilities and tasks
		problem.schedules = component.getCommunicationInterface().retrieveSchedules(problem.from, problem.to);
		
		// use first found solver
		for(OptimizationService service : component.getOptimizationServices()) {
			if(service.canSolve(TargetScheduleProblem.class, TargetScheduleSolution.class)) {
				// solve problem					
				TargetScheduleSolution solution = service.solve(problem, TargetScheduleProblem.class, TargetScheduleSolution.class);
				
				// schedule tasks
				component.getCommunicationInterface().scheduleTasks(solution.tasks, problem.schedules);
				
				// report if deviation from target schedule is to large
				if(false == missingTargetSchedule && Math.abs(solution.expectedMaximumDeviation) > configuration.scheduleDeviationReportingThreshold) {
					// report deviation
			        // create data object
			        SchedulePublishingData data = new SchedulePublishingData();				        
			        Instant now = timeService.nowAsInstant();
			        data.from = now.getEpochSecond();
			        data.to = now.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS).toEpochSecond();
			        data.initial = false;
			        // queue publication
				    component.getOptimizationQueue().queueSchedulePublication(data);
				}
					
				// finished
				break;
			}
		}		
		
		component.getData().mostRecentTargetScheduleOptimization = timeService.now();
	}

	/**
	 * PublicSchedule publishing
	 * 
	 * Starts an optimization and then tries to publish the result. 
	 * If publishing fails, another try (including a new optimization) is performed some time in the future.
	 * 
	 * @param publishingData
	 */
	private void publishSchedule(SchedulePublishingData publishingData) {		
		boolean schedulePublished = false;
		
		try {
			// validate input
			if(publishingData.to < timeService.now()) {
				log.warning("Optimization time frame in past. Cancelling.");
				return;
			}
			
			// collect problem data
			SchedulingProblem problem = new SchedulingProblem();
			// make sure $from is at the begin of a slot
			problem.from = ensureTimeIsAtSlotStart(ZonedDateTime.ofInstant(Instant.ofEpochSecond(publishingData.from), ZoneId.systemDefault())).toEpochSecond();
			problem.to = publishingData.to;
			problem.slotLength = configuration.scheduleOptimizationSlotLength;
			problem.optimizationTimeBuffer = configuration.optimizationTimeBuffer;
			problem.flexibilityAdaptionBuffer = configuration.flexibilityAdaptationBuffer;
			
			// get forecasts, use first suitable service
			problem.electricityDemand = getConsumptionForecast(problem.from, problem.to);
			problem.electricityProduction = getProductionForecast(problem.from, problem.to);
			
			// get all available flexibilities and tasks
			problem.schedules = component.getCommunicationInterface().retrieveSchedules(problem.from, problem.to);
			
			// use first found solver
			for(OptimizationService service : component.getOptimizationServices()) {
				if(service.canSolve(SchedulingProblem.class, SchedulingSolution.class)) {
					// solve problem					
					SchedulingSolution solution = service.solve(problem, SchedulingProblem.class, SchedulingSolution.class);
					
					log.finest("Scheduling tasks.");
					// schedule tasks
					component.getCommunicationInterface().scheduleTasks(solution.tasks, problem.schedules);
	
					if(false == publishingData.initial) {
						// check if schedule deviates from target
						log.finest("Checking schedule for target deviations.");
						
						boolean publish = false;
						PublicSchedule targetSchedule = component.getTargetSchedule();
						if(null != targetSchedule) {
							// determine offset between target schedule start and solution schedule start
							long offsetInSeconds = solution.schedule.startingTime - targetSchedule.startingTime;
							int slotOffset = (int)(offsetInSeconds / configuration.scheduleOptimizationSlotLength);
							// now check whether the absolute deviation exceeds the threshold for reporting
							for(int i = 0; i < solution.schedule.consumption.length; i++) {
								if(Math.abs(targetSchedule.consumption[i + slotOffset] - solution.schedule.consumption[i]) > 
									configuration.scheduleDeviationReportingThreshold) {
									
									// deviation too large
									log.finest(
											"Consumption target='" + targetSchedule.consumption[i + slotOffset] + 
											"' deviates to much from solution='" + 
											solution.schedule.consumption[i] + "'");
									publish = true;
									break;
								}
								if(Math.abs(targetSchedule.production[i + slotOffset] - solution.schedule.production[i]) > 
									configuration.scheduleDeviationReportingThreshold) {
									
									// deviation too large
									log.finest(
											"Production target='" + targetSchedule.production[i + slotOffset] + 
											"' deviates to much from solution='" + 
											solution.schedule.production[i] + "'");
									publish = true;
									break;
								}
							}
						} else {
							log.finest("No target schedule available.");
							publish = true;
						}
						
						// publishing necessary?
						if(true == publish) {
							log.finest("Publishing schedule update.");
							// publish solution
							schedulePublished = Scheduler.getFmsCommunicationService().publishSchedule(solution.schedule, solution.flexibility, 
									PublicationType.ScheduleUpdate);
						} else {
							// nothing to publish, hence we are done
							schedulePublished = true;
						}
					} else {
						
						log.finest("Publishing schedule.");
						// publish solution
						schedulePublished = Scheduler.getFmsCommunicationService().publishSchedule(solution.schedule, solution.flexibility, 
								PublicationType.InitialSchedule);
						
						// now update own target schedule to follow this publication
						// update is only done for initial publication, since no target schedule has been set by FMS yet
						log.finest("Updating target schedule using optimization solution.");
						component.setTargetSchedule(solution.schedule);	
					}
					// finished
					break;
				}
			}		
		} finally {
			if(!schedulePublished) {
				// failure !
				if(publishingData.initial) {
					log.warning("Initial schedule publication failed!");
					component.setIncompleteInitialPublication(publishingData);
				} else {
					log.warning("Updated schedule publication failed!");
					component.setIncompleteUpdatePublication(publishingData);
				}
				// persistence
				component.saveState();
			} else {
				log.info("Successfully finished schedule publication.");
				// reset any publication attempt for this type of publication
				if(publishingData.initial) {
					component.setIncompleteInitialPublication(null);
					// Sets the time for the next publication based on now.
					// Keep in mind that this time is only used as reference on a scheduler restart to make sure at least one schedule is published.
					// Using now + 1 day should be sufficient, since it is rewritten after every future publication (hopefully without previous errors).
					component.getData().latestInitialSchedulePublication = timeService.now();
				} else {
					component.setIncompleteUpdatePublication(null);
					component.getData().latestScheduleUpdatePublication = timeService.now();
				}
				// persistence
				component.saveState();
			}
		}
	}
}
