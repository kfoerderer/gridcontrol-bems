package de.fzi.osh.optimization.schedule.implementation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.com.fms.PublicFlexibility;
import de.fzi.osh.com.fms.PublicSchedule;
import de.fzi.osh.optimization.OptimizationService;
import de.fzi.osh.optimization.Problem;
import de.fzi.osh.optimization.Solution;
import de.fzi.osh.optimization.schedule.ScheduleData;
import de.fzi.osh.optimization.schedule.SchedulingProblem;
import de.fzi.osh.optimization.schedule.SchedulingSolution;
import de.fzi.osh.time.TimeService;
import de.fzi.osh.types.flexibilities.Flexibility;
import de.fzi.osh.types.flexibilities.Task;
import de.fzi.osh.types.math.IntInterval;

/**
 * Service for optimizing the schedule in order to achieve self consumption of generated electricity.
 * 
 * Procedure:
 * 	In: forecasts
 * 	In: flexibilities
 * 	In: Time window (from, to); Must fit an integer number of time slots (ignoring leap seconds / minutes)
 * 	In: time slot length
 * 	In: tasks \ {tasks created from (still available) flexibilities that have not started yet}
 * - compute expected (fixed) consumption = forecast + tasks
 * - minimize amount of electricity sold and bought
 * - minimize flexibility usage variation (idea: integral of absolute derivative)
 * 
 * @author K. Foerderer
 *
 */
@Component(enabled=true, service=OptimizationService.class)
public class ScheduleOptimizationService implements OptimizationService{

	private static Logger log = Logger.getLogger(ScheduleOptimizationService.class.getName());
	
	private TimeService timeService;
	
	@Override
	public Map<Class<?>, Class<?>[]> getCapabilities() {
		// create mapping
		Map<Class<?>, Class<?>[]> capabilities = new HashMap<Class<?>, Class<?>[]>();
		capabilities.put(SchedulingProblem.class, new Class<?>[] {SchedulingSolution.class});
		
		return capabilities;
	}
	
	@Override
	public boolean canSolve(Class<? extends Problem> problem, Class<? extends Solution> solution) {
		return problem.equals(SchedulingProblem.class) && solution.equals(SchedulingSolution.class);
	}
	

	@Override
	public <P extends Problem, S extends Solution> S solve(P problem, Class<P> problemClass, Class<S> solutionClass) {
		if(!canSolve(problemClass, solutionClass)) {
			log.warning("Problem- and/or solution-class not supported.");
			return null;
		}
		log.info("Starting schedule optimization.");
		
		// initialization
		SchedulingProblem schedulingProblem = (SchedulingProblem) problem;
		SchedulingSolution solution = new SchedulingSolution();
		
		solution.from = schedulingProblem.from;
		solution.to = schedulingProblem.to;
		solution.schedule = new PublicSchedule();
		solution.tasks = new HashMap<UUID, Task[]>();
		
		/* can only do optimizations for future
		// this should never happen, but just in case
		while(schedulingProblem.from.toEpochSecond() < Instant.now().getEpochSecond() + schedulingProblem.optimizationTimeBuffer) {
			schedulingProblem.from = schedulingProblem.from.plus(schedulingProblem.slotLength, ChronoUnit.SECONDS);
			log.warning("Scheduling problem time window adapted to be in future.");
		}*/
		
		// pre-analysis
		int[] total = new int[schedulingProblem.electricityDemand.length]; // Wh
		for(int i = 0; i < schedulingProblem.electricityDemand.length; i++) {
			total[i] = schedulingProblem.electricityProduction[i] + schedulingProblem.electricityDemand[i]; // Consumption > 0; Feed In < 0			
		}
		
		// solving (heuristic)
		int[] target = new int[total.length]; // target schedule = 0 => self consumption
		
		for(Entry<UUID, ScheduleData> entry : schedulingProblem.schedules.entrySet()) {
			ScheduleData scheduleData = entry.getValue();
			
			// filter for all relevant flexibilities and tasks
			Map<Integer, Task> relevantTasks = new HashMap<Integer, Task>();
			Map<Integer, Flexibility> relevantFlexibilities = new HashMap<Integer, Flexibility>();
			
			// first look which flexibilities have been turned into tasks and which of those can still be modified
			for(Task task : scheduleData.tasks) {
				// is this task generated from a flexibility and can it still be changed?
				// only do changes to tasks that have not started before $from, since they are most likely relevant for the target schedule optimization
				if(task.flexibilityId >= 0 && (task.adaptable || task.startingTime >= schedulingProblem.from)) {
					relevantTasks.put(task.id, task);
					// find flexibility for this task
					for(Flexibility flexibility : scheduleData.flexibilities) {
						if(flexibility.id == task.flexibilityId) {
							relevantFlexibilities.put(task.flexibilityId, flexibility);
						}
					}
				}
			}
			// now find all flexibilities that have no task associated
			for(Flexibility flexibility : scheduleData.flexibilities) {
				if(flexibility.taskId < 0 && flexibility.stoppingTime.max - flexibility.runningTime.min >= schedulingProblem.from) {
					relevantFlexibilities.put(flexibility.id, flexibility);
				}
			}
			
			List<Task> taskList = new ArrayList<Task>();
			// apply a simple self consumption heuristic
			for(Flexibility flexibility : relevantFlexibilities.values()) {
				// get the corresponding task if there is any
				Task task = relevantTasks.get(flexibility.taskId);
				if(null == task) {
					// no task, create one
					log.info("Creating a new task for flexibility " + flexibility.id + ".");
					task = new Task();
					task.id = -1; // this task doesn't exist yet
					task.flexibilityId = flexibility.id;
					task.adaptable = flexibility.adaptable;
					if(flexibility.stoppingTime.min - flexibility.runningTime.max < schedulingProblem.from) { // don't start before $from
						task.startingTime = schedulingProblem.from;
					} else {
						task.startingTime = flexibility.stoppingTime.min - flexibility.runningTime.max;
					}
					task.runningTime = flexibility.runningTime.max;
				}
				
				// initialization
				NavigableMap<Integer, IntInterval> powerCorridor = flexibility.powerCorridor;
				NavigableMap<Integer, IntInterval> energyCorridor = flexibility.energyCorridor;
			
				NavigableMap<Integer, Integer> taskPower = new TreeMap<Integer, Integer>();
				taskPower.put(0, 0);
				
				int relativeTime = (int)(schedulingProblem.from - task.startingTime); // = time since start of task
				long energy = 0; // in Ws, equals 0 at optimization start
				// $from is at the begin of a slot => slot = 0 is valid
				int slot = 0; // relative slot since $currentSlotBegin
				int nextSlotBegin = 0; // as time since start of task
				double slotEnergy = 0; // in Wh
				
				// has the task started yet?
				if(relativeTime < 0) {
					relativeTime = 0;
					slot = -relativeTime / schedulingProblem.slotLength;
					// first slot, everything is fine
					nextSlotBegin = schedulingProblem.slotLength - (int)(task.startingTime - schedulingProblem.from) % schedulingProblem.slotLength;
				} else {
					// task is already running, hence previous slots have to be considered
					nextSlotBegin = (int)(schedulingProblem.from + schedulingProblem.slotLength - task.startingTime); // again: $from is at slot start
				}

				// TODO: optimization when exceeding $to
				while(task.startingTime + relativeTime < schedulingProblem.to && relativeTime < task.runningTime) {
					
					// next time = min { next power corridor constraint, next energy corridor constraint, next slot begin}
					Integer key = powerCorridor.higherKey(relativeTime);
					int nextTime = (key == null ? Integer.MAX_VALUE : key); // relative
					key = energyCorridor.higherKey(relativeTime);
					if(null != key && key < nextTime) {
						nextTime = key;
					}
					if(nextSlotBegin < nextTime) {
						nextTime = nextSlotBegin;
					}
					
					// valid power constraint up to $nextTime
					IntInterval powerConstraint = powerCorridor.floorEntry(relativeTime).getValue();
					
					// min and max power
					int min = powerConstraint.min;
					int max = powerConstraint.max;
					
					// retrieve energy constraint for this point in time
					IntInterval energyConstraint = flexibility.getEnergyConstraint(relativeTime);
					// try to apply buffer
					int minEnergy = energyConstraint.min + schedulingProblem.flexibilityAdaptionBuffer; // Ws
					int maxEnergy = energyConstraint.max - schedulingProblem.flexibilityAdaptionBuffer; // Ws
					if(minEnergy > maxEnergy) {
						// there is not enough space for the buffer, use original values
						minEnergy = energyConstraint.min;
						maxEnergy = energyConstraint.max;
					}
					
					// derive min and max power from energy constraint
					int minFromEnergy = (int)((minEnergy - energy) / (nextTime - relativeTime));
					int maxFromEnergy = (int)((maxEnergy - energy) / (nextTime - relativeTime));
					if(minFromEnergy > min) {
						min = minFromEnergy;
					}
					if(maxFromEnergy < max) {
						max = maxFromEnergy;
					}
					if(max < min) {
						// debug
						log.severe("Max power < Min power.");
						log.info("Power constraint: [" + powerConstraint.min + ", " + powerConstraint.max + "]");
						log.info("Energy constraint: [" + energyConstraint.min + ", " + energyConstraint.max + "]; Current Energy: " + energy);
						min = max = 0;
					}
					
					// average power in this time slot | Note: Wh * 60 min/h * 60 s/min = Ws
					// target - total < 0 => uses too much energy => target < 0 in order to provide energy
					// [!] also, since target.length = total.length no offset has to be applied (compare to target schedule opt.)
					int targetPower = (target[slot] - total[slot]) * 60 * 60 / (schedulingProblem.slotLength); // W
					
					if(targetPower <= min) {
						taskPower.put(relativeTime, min);
					} else if(targetPower >= max) {
						taskPower.put(relativeTime, max);
					} else {
						taskPower.put(relativeTime, targetPower);
					}
					
					// step forward in time
					energy += (nextTime - relativeTime) * taskPower.get(relativeTime); // Ws
					slotEnergy += (nextTime - relativeTime) * taskPower.get(relativeTime) / (60 * 60.0); // Wh  
					
					relativeTime = nextTime;
					if(relativeTime == nextSlotBegin) {
						// adjust slot consumption
						total[slot] += slotEnergy;
						slotEnergy = 0;
						slot++;
						nextSlotBegin += schedulingProblem.slotLength;
					}
				}
				taskPower.put(task.runningTime, 0);
				task.power = taskPower;
				taskList.add(task);
			}
			
			// add to solution
			solution.tasks.put(entry.getKey(), taskList.toArray(new Task[taskList.size()]));
		}
		solution.schedule.timestamp = timeService.nowAsInstant().getEpochSecond();
		solution.schedule.startingTime = schedulingProblem.from;
		solution.schedule.consumption = new int[total.length];
		solution.schedule.production = new int[total.length];
		for(int i = 0; i < solution.schedule.production.length; i++) {
			solution.schedule.consumption[i] = total[i] - schedulingProblem.electricityProduction[i]; // total = demand + production => demand = total - production
			
			solution.schedule.production[i] = schedulingProblem.electricityProduction[i]; // negative values
		}
		solution.schedule.flexibleConsumption = new int[total.length]; // = 0 by interface definition/ battery loading doesn't make sense
		solution.schedule.flexibleProduction = new int[total.length]; // = 0 by definition
		solution.schedule.slotLength = schedulingProblem.slotLength;
		
		// compute result
		int expectedElectricityBought = 0;
		int expectedElectricitySold = 0;
		for(int i = 0; i < schedulingProblem.electricityDemand.length; i++) {
			if(total[i] < 0) {
				expectedElectricitySold += -total[i];
			} else {
				expectedElectricityBought += total[i];
			}			
		}
		
		// aggregate flexibilities for publishing
		solution.flexibility = aggregate(schedulingProblem, solution.tasks);
		
		// set missing fields and return solution
		solution.expectedElectricityBought = expectedElectricityBought;
		solution.expectedElectricitySold = expectedElectricitySold;
		log.info("Finished schedule optimization.");
		return solutionClass.cast(solution);
	}
	
	/**
	 * Generates the public flexibility.
	 * 
	 * @param from
	 * @param to
	 * @param schedules
	 * @param tasksToSchedule
	 * @return
	 */
	private PublicFlexibility aggregate(SchedulingProblem schedulingProblem, Map<UUID, Task[]> tasksToSchedule) {
		PublicFlexibility result = new PublicFlexibility();
		
		result.timestamp = timeService.now();
		result.startingTime = schedulingProblem.from;
		result.slotLength = schedulingProblem.slotLength;
		
		double[] powerMin = new double[schedulingProblem.electricityDemand.length];
		double[] powerMax = new double[schedulingProblem.electricityDemand.length];
		int[] energyMin = new int[schedulingProblem.electricityDemand.length]; // Ws
		int[] energyMax = new int[schedulingProblem.electricityDemand.length]; // Ws
		
		// debug
		String debugMessage = "Aggregating flexibility for:";
		for(Entry<UUID, Task[]> entry : tasksToSchedule.entrySet()) {
			debugMessage += " " + entry.getKey() + "[";
			for(Task task : entry.getValue()) {
				debugMessage += task.id + ", ";
			}
			// remove ", "
			debugMessage = debugMessage.substring(0,debugMessage.length() - 2) + "],";
		}
		// remove ","
		log.finest(debugMessage.substring(0,debugMessage.length() - 1));
		
		// for every task
		for(Entry<UUID, Task[]> entry : tasksToSchedule.entrySet()) {
			for(Task task : entry.getValue()) {
				
				// get flexibility
				Flexibility flexibility = null;
				for(Flexibility f : schedulingProblem.schedules.get(entry.getKey()).flexibilities) {
					if(f.id == task.flexibilityId) {
						flexibility = f;
						break;
					}
				}
				
				if(null == flexibility) {
					log.severe("Task without flexibility id. Ignoring it.");
				} else {
					Flexibility taskFlexibility = flexibility.determineTaskFlexibility(task);
					
					NavigableMap<Integer,IntInterval> powerCorridor = taskFlexibility.powerCorridor;
					NavigableMap<Integer,IntInterval> energyCorridor = taskFlexibility.energyCorridor;
										
					int relativeTime = (int)(schedulingProblem.from - task.startingTime); // = time since start of task
					// $from is at the begin of a slot => slot = 0 is valid
					int slot = 0; // relative slot since $currentSlotBegin
					int nextSlotBegin = 0; // as time since start of task
					
					// has the task started yet?
					if(relativeTime < 0) {
						relativeTime = 0;
						slot = -relativeTime / schedulingProblem.slotLength;
						// first slot, everything is fine
						nextSlotBegin = schedulingProblem.slotLength - (int)(task.startingTime - schedulingProblem.from) % schedulingProblem.slotLength;
					} else {
						// task is already running, hence previous slots have to be considered
						nextSlotBegin = (int)(schedulingProblem.from + schedulingProblem.slotLength - task.startingTime); // again: $from is at slot start
					}
					
					while(task.startingTime + relativeTime < schedulingProblem.to && relativeTime < task.runningTime) {

						// next time = min { next power corridor constraint, next energy corridor constraint, next slot beginn}
						Integer key = powerCorridor.higherKey(relativeTime);
						int nextTime = (key == null ? Integer.MAX_VALUE : key);
						key = energyCorridor.higherKey(relativeTime);
						if(null != key && key < nextTime) {
							nextTime = key;
						}
						if(nextSlotBegin < nextTime) {
							nextTime = nextSlotBegin;
						}
					
						// for power, use average
						IntInterval power = powerCorridor.floorEntry(relativeTime).getValue();
						powerMin[slot] += power.min * (nextTime - relativeTime) / (double)schedulingProblem.slotLength;
						powerMax[slot] += power.max * (nextTime - relativeTime) / (double)schedulingProblem.slotLength;
						
						// for energy, the slot begin and end are relevant

						relativeTime = nextTime;
						if(relativeTime == nextSlotBegin) {
							IntInterval energy = taskFlexibility.getEnergyConstraint(relativeTime);
							energyMin[slot] += energy.min + schedulingProblem.flexibilityAdaptionBuffer;
							energyMax[slot] += energy.max - schedulingProblem.flexibilityAdaptionBuffer;
							
							if(energyMin[slot] > energyMax[slot]) {
								// since the flexibility is based on a task, 0 is a valid option
								energyMin[slot] = energyMax[slot] = 0;
							}
							
							slot++;
							nextSlotBegin += schedulingProblem.slotLength;
						}
					}
				}
			}
		}
		
		// package data
		result.powerCorridor = new IntInterval[powerMin.length];
		result.energyCorridor = new IntInterval[powerMax.length];
		for(int i = 0; i < powerMin.length; i++) {
			result.powerCorridor[i] = new IntInterval((int)powerMin[i], (int)powerMax[i]);
			// transform from Ws to Wh
			result.energyCorridor[i] = new IntInterval(energyMin[i] / (60 * 60), energyMax[i] / (60 * 60)); 
		}
		
		return result;
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
