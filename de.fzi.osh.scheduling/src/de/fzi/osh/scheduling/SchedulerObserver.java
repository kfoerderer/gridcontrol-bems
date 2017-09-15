package de.fzi.osh.scheduling;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.core.configuration.BaseConfiguration;
import de.fzi.osh.core.configuration.BaseConfiguration.MeterConfiguration;
import de.fzi.osh.core.oc.Controller;
import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.core.oc.Observer;
import de.fzi.osh.scheduling.configuration.SchedulerConfiguration;
import de.fzi.osh.scheduling.dataobjects.EnabledData;
import de.fzi.osh.scheduling.dataobjects.FlexibilitiesChangedData;
import de.fzi.osh.scheduling.dataobjects.MeterMeasurementData;
import de.fzi.osh.scheduling.dataobjects.TargetScheduleData;
import de.fzi.osh.types.flexibilities.Task;
import de.fzi.osh.scheduling.dataobjects.ScheduleMonitoringData;

/**
 * Scheduling service observer
 * 
 * @author K. Foerderer
 *
 */
public class SchedulerObserver extends Observer<Scheduler, SchedulerConfiguration>{

	private static Logger log = Logger.getLogger(SchedulerObserver.class.getName());
	
	private boolean enabled = true;
	
	// latest meter data
	private Map<UUID, MeterMeasurementData> consumptionData;
	private Map<UUID, MeterMeasurementData> productionData;
	private Map<UUID, MeterMeasurementData> batteryData;
	// meter data on last adaptation
	private Map<UUID, MeterMeasurementData> previousAdaptationConsumptionData;
	private Map<UUID, MeterMeasurementData> previousAdaptationProductionData;
	private Map<UUID, MeterMeasurementData> previousAdaptationBatteryData;
	// sets for faster meter identification
	private Set<UUID> productionMeterUUIDs;
	private Set<UUID> consumptionMeterUUIDs;
	private Set<UUID> batteryMeterUUIDs;
	
	public SchedulerObserver(Scheduler component, Controller<Scheduler, SchedulerConfiguration> controller) {
		super(component, controller);
	}
	
	@Override
	public void initialize() {
		super.initialize();
		
		BaseConfiguration baseConfiguration = component.getBaseConfiguration();
		
		consumptionMeterUUIDs = new HashSet<UUID>();
		if(null == baseConfiguration.consumptionMeterUUIDs) {
			log.warning("No consumption meter defined.");
		} else {
			Collections.addAll(consumptionMeterUUIDs, baseConfiguration.consumptionMeterUUIDs);
		}
		consumptionData = new HashMap<UUID, MeterMeasurementData>();
		previousAdaptationConsumptionData = new HashMap<UUID, MeterMeasurementData>(); 
		
		productionMeterUUIDs = new HashSet<UUID>();
		if(null == baseConfiguration.productionMeterUUIDs) {
			log.warning("No production meter defined.");
		} else {
			Collections.addAll(productionMeterUUIDs, baseConfiguration.productionMeterUUIDs);
		}
		productionData = new HashMap<UUID, MeterMeasurementData>();
		previousAdaptationProductionData = new HashMap<UUID, MeterMeasurementData>();
		
		batteryMeterUUIDs = new HashSet<UUID>();
		if(null == baseConfiguration.batteryMeterUUIDs) {
			log.warning("No battery meter defined.");
		} else {
			Collections.addAll(batteryMeterUUIDs, baseConfiguration.batteryMeterUUIDs);
		}
		batteryData = new HashMap<UUID, MeterMeasurementData>();
		previousAdaptationBatteryData = new HashMap<UUID, MeterMeasurementData>();
	}
	
	@Override
	public void update(DataObject data) {
		
		// enabled signal
		if(data instanceof EnabledData) {
			enabled = ((EnabledData) data).enabled;
			controller.update(data);
		}				
		
		// save measurements
		else if(data instanceof MeterMeasurementData) {
			MeterMeasurementData measurementData = (MeterMeasurementData) data; 

			if(consumptionMeterUUIDs.contains(measurementData.uuid)) {
				MeterMeasurementData savedData = consumptionData.get(measurementData.uuid);
				if(savedData == null || savedData.time < measurementData.time) {
					// make sure its new data
					consumptionData.put(measurementData.uuid, measurementData);
				}
			} else if(productionMeterUUIDs.contains(measurementData.uuid)) {
				MeterMeasurementData savedData = productionData.get(measurementData.uuid);
				if(savedData == null || savedData.time < measurementData.time) {
					// make sure its new data
					productionData.put(measurementData.uuid, measurementData);
				}
			} else if(batteryMeterUUIDs.contains(measurementData.uuid)) {
				MeterMeasurementData savedData = batteryData.get(measurementData.uuid);
				if(savedData == null || savedData.time < measurementData.time) {
					// make sure its new data
					batteryData.put(measurementData.uuid, measurementData);
				}
			}
		}		
		
		
		// compensate deviations from schedule (periodical event)
		else if(enabled && data instanceof ScheduleMonitoringData) {
			ScheduleMonitoringData adaptationData = (ScheduleMonitoringData) data;
			 
			// is current and previous data available?
			if(consumptionData.size() > 0 || productionData.size() > 0 || batteryData.size() > 0) {
				
				// fill object with data
				
				// It is sufficient to look at the total energy, take for example:
				// A measurement with 0.01W accuracy every 10s, then an average of at least 3.6W is detectable.
				
				adaptationData.consumption = 0;				
				for(Entry<UUID, MeterMeasurementData> entry : consumptionData.entrySet()) {
					MeterMeasurementData previousMeasurement = previousAdaptationConsumptionData.get(entry.getKey());
					if(null == previousMeasurement) {
						adaptationData.consumption += entry.getValue().totalActivePower / 10.0; // fix unit
					} else {
						adaptationData.consumption += 
								(entry.getValue().totalActiveEnergyP - previousMeasurement.totalActiveEnergyP) / 
								(double)(entry.getValue().time - previousMeasurement.time) * 3600 / 100.0; // fix unit
					}					
				}
				
				adaptationData.production = 0;
				for(Entry<UUID, MeterMeasurementData> entry : productionData.entrySet()) {
					MeterMeasurementData previousMeasurement = previousAdaptationProductionData.get(entry.getKey());
					if(null == previousMeasurement) {
						adaptationData.production += entry.getValue().totalActivePower / 10.0; // fix unit
					} else {
						adaptationData.production -= 
								(entry.getValue().totalActiveEnergyN - previousMeasurement.totalActiveEnergyN) / 
								(double)(entry.getValue().time - previousMeasurement.time) * 3600 / 100.0; // fix unit
					}
				}
				
				// consider meter configuration
				if(component.getBaseConfiguration().meterConfiguration == MeterConfiguration.ConsumptionIncludingProduction) {
					adaptationData.consumption -= adaptationData.production;
				}
				
				// if there is a batteryData meter, use its data
				adaptationData.battery = 0;
				if(batteryData.size() > 0) {	
					for(Entry<UUID, MeterMeasurementData> entry : batteryData.entrySet()) {
						MeterMeasurementData previousMeasurement = previousAdaptationBatteryData.get(entry.getKey());
						if(null == previousMeasurement) {
							adaptationData.battery += entry.getValue().totalActivePower / 10.0; // fix unit
						} else {
							adaptationData.battery += 
									((entry.getValue().totalActiveEnergyP - previousMeasurement.totalActiveEnergyP) -
									(entry.getValue().totalActiveEnergyN - previousMeasurement.totalActiveEnergyN)) / 
									(double)(entry.getValue().time - previousMeasurement.time) * 3600 / 100.0; // fix unit
						}
					}						
				} 

				// create shallow copies of the meter data
				previousAdaptationConsumptionData = new HashMap<UUID, MeterMeasurementData>(consumptionData);
				previousAdaptationProductionData = new HashMap<UUID, MeterMeasurementData>(productionData);
				previousAdaptationBatteryData = new HashMap<UUID, MeterMeasurementData>(batteryData);
				
				// meter data copies are created before trying to get battery data from the batteries task for stability reasons
				if(batteryData.size() == 0){
					// there is no batteryData meter, try to use task data
					BaseConfiguration baseConfiguration = component.getBaseConfiguration();
					
					for(UUID uuid : baseConfiguration.batteryUUIDs) {
						// acquire task data
						Map<Integer, Task> batteryTasks = component.getAdaptableTasks().get(uuid);
						
						if(null != batteryTasks) {
							// find task
							long now = Scheduler.getTimeService().now();
							for(Task task : batteryTasks.values()) {
								if(task.startingTime <= now && now < task.startingTime + task.runningTime) {
									// found it, get power
									adaptationData.battery += task.power.floorEntry((int)(now - task.startingTime)).getValue();
								}
							}
						}
					}
				}				
				
				PublicSchedule targetSchedule = component.getTargetSchedule();
				// make sure there is a target schedule
				if(null != targetSchedule) {
					long scheduleStartingTime = targetSchedule.startingTime;
					int slot = (int) ((Scheduler.getTimeService().now() - scheduleStartingTime) / (targetSchedule.slotLength));
					
					// make sure there is a target schedule					
					if(slot < targetSchedule.consumption.length) {
						adaptationData.schedule  = (double) targetSchedule.consumption[slot] + targetSchedule.flexibleConsumption[slot]
								+ targetSchedule.production[slot] + targetSchedule.flexibleProduction[slot]; 
						
						// Wh/slot -> W
						adaptationData.schedule *= (60 * 60.0) / targetSchedule.slotLength;
						
						// pass to controller
						controller.update(adaptationData);
					} else {
						// adapt for self consumptionData
						adaptationData.schedule = 0;
						// pass to controller
						controller.update(adaptationData);
					}
				} else {
					// adapt for self consumptionData
					adaptationData.schedule = 0;
					// pass to controller
					controller.update(adaptationData);
				}
			}
		}		
		
		
		// new flexibilities
		else if(data instanceof FlexibilitiesChangedData) {
			FlexibilitiesChangedData flexibilitiesData = (FlexibilitiesChangedData) data;
			controller.update(flexibilitiesData);
		}		
		
		
		// pre-evaluate schedule
		else if(data instanceof TargetScheduleData) {
			TargetScheduleData scheduleData = (TargetScheduleData) data;			
			if(false == scheduleData.mustUpdate) {
				// TODO: evaluation
				scheduleData.mustUpdate = true;
			}
			controller.update(scheduleData);
		}
		
		
		else {
			// pass data to controller
			controller.update(data);	
		}
	}
}
