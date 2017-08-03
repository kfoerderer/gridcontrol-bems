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

import de.fzi.osh.optimization.OptimizationService;
import de.fzi.osh.optimization.Problem;
import de.fzi.osh.optimization.Solution;
import de.fzi.osh.optimization.schedule.ScheduleData;
import de.fzi.osh.optimization.schedule.TargetScheduleProblem;
import de.fzi.osh.optimization.schedule.TargetScheduleSolution;
import de.fzi.osh.types.flexibilities.Flexibility;
import de.fzi.osh.types.flexibilities.Task;
import de.fzi.osh.types.math.IntInterval;

/**
 * Service for optimizing the schedule in order to achieve target schedule compliance.
 * 
 * Procedure:
 * 	In: forecasts
 * 	In: flexibilities
 * 	In: tasks \ {tasks created from (still available) flexibilities that have not started yet}
 * - compute expected (fixed) consumption = forecast + tasks
 * - minimize total deviation
 * - minimize within this solution the max. deviation
 * 
 * @author K. Foerderer
 *
 */
@Component(enabled=true, service=OptimizationService.class)
public class TargetScheduleOptimizationService implements OptimizationService{

	private static Logger log = Logger.getLogger(TargetScheduleProblem.class.getName());
	
	@Override
	public Map<Class<?>, Class<?>[]> getCapabilities() {
		// create mapping
		Map<Class<?>, Class<?>[]> capabilities = new HashMap<Class<?>, Class<?>[]>();
		capabilities.put(TargetScheduleProblem.class, new Class<?>[] {TargetScheduleSolution.class});
		
		return capabilities;
	}
	
	@Override
	public boolean canSolve(Class<? extends Problem> problem, Class<? extends Solution> solution) {
		return problem.equals(TargetScheduleProblem.class) && solution.equals(TargetScheduleSolution.class);
	}

	@Override
	public <P extends Problem, S extends Solution> S solve(P problem, Class<P> problemClass, Class<S> solutionClass) {
		if(!canSolve(problemClass, solutionClass)) {
			log.warning("Problem- and/or solution-class not supported.");
			return null;
		}
		log.info("Starting target schedule optimization.");
		
		// initialization
		TargetScheduleProblem schedulingProblem = (TargetScheduleProblem) problem;
		TargetScheduleSolution solution = new TargetScheduleSolution();
		
		solution.from = schedulingProblem.from;
		solution.to = schedulingProblem.to;
		solution.tasks = new HashMap<UUID, Task[]>();
		
		// pre-analysis
		int[] total = new int[schedulingProblem.electricityDemand.length];
		for(int i = 0; i < schedulingProblem.electricityDemand.length; i++) {
			total[i] = schedulingProblem.electricityProduction[i] + schedulingProblem.electricityDemand[i]; // Consumption > 0; Feed In < 0			
		}
		int targetSlotOffset = (int) (schedulingProblem.currentSlotBegin - schedulingProblem.targetSchedule.startingTime) / schedulingProblem.slotLength;
		
		// solving (heuristic)
		solution.tasks = new HashMap<UUID, Task[]>();
		
		// determine target
		int[] target = new int[schedulingProblem.targetSchedule.consumption.length];
		for(int i = 0; i < target.length; i++) {
			target[i] = schedulingProblem.targetSchedule.consumption[i] + schedulingProblem.targetSchedule.flexibleConsumption[i]
					+ schedulingProblem.targetSchedule.production[i] + schedulingProblem.targetSchedule.flexibleProduction[i];
		}
		
		for(Entry<UUID, ScheduleData> entry : schedulingProblem.schedules.entrySet()) {
			ScheduleData scheduleData = entry.getValue();
			
			// filter for all relevant flexibilities and tasks
			Map<Integer, Task> relevantTasks = new HashMap<Integer, Task>();
			Map<Integer, Flexibility> relevantFlexibilities = new HashMap<Integer, Flexibility>();
			
			// first look which flexibilities have been turned into tasks and which of those can still be modified
			for(Task task : scheduleData.tasks) {
				// is this task generated from a flexibility and can it still be changed?
				// only do changes to tasks that have not started before $from + buffer, since they are most likely relevant for the target schedule optimization
				if(task.flexibilityId >= 0 && (task.adaptable || task.startingTime >= schedulingProblem.from + schedulingProblem.optimizationTimeBuffer)) {
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
				if(flexibility.taskId < 0 && flexibility.stoppingTime.max - flexibility.runningTime.min >= schedulingProblem.from + schedulingProblem.optimizationTimeBuffer) {
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
				int slot = 0; // relative slot since $currentSlotBegin
				int nextSlotBegin = 0; // as time since start of task
				double slotEnergy = 0; // in Wh
				
				// has the task started yet?
				if(relativeTime < 0) {
					relativeTime = 0;
					slot = -relativeTime / schedulingProblem.slotLength;
					// first slot, everything is fine
					nextSlotBegin = schedulingProblem.slotLength - (int)(task.startingTime - schedulingProblem.currentSlotBegin) % schedulingProblem.slotLength;
				} else {
					// since a buffer is applied $from is not necessarily in slot 0
					slot = (int)((schedulingProblem.from - schedulingProblem.currentSlotBegin) / schedulingProblem.slotLength);
					// task is already running, hence previous slots have to be considered
					nextSlotBegin = (int)(schedulingProblem.currentSlotBegin + schedulingProblem.slotLength - task.startingTime);
					if(nextSlotBegin < relativeTime) {
						// $from is in slot nr. 1 not slot nr. 0
						nextSlotBegin += schedulingProblem.slotLength;
					}
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
						log.fine("Not applying energy buffer.");
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
						log.severe("Illegal power schedule for task. [max=" + max + ", min=" + min + ", powerC=" + powerConstraint.toString() + ", energyC=" + energyConstraint.toString()+ "]");
						log.severe("energy=" + energy + ", minFromEnergy=" + minFromEnergy + ", maxFromEnergy=" + maxFromEnergy);
						min = max = 0;
					}
					
					// average power in this time slot | Note: Wh * 60 min/h * 60 s/min = Ws
					// target - total < 0 => uses too much energy => target < 0 in order to provide energy
					int targetPower = (target[slot + targetSlotOffset] - total[slot]) * 60 * 60 / (schedulingProblem.slotLength); // W
					
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
		
		// compute result
		int expectedCumulativeDeviation = 0;
		int expectedMaximumDeviation = 0;
		for(int i = 0; i < schedulingProblem.electricityDemand.length; i++) {
			expectedCumulativeDeviation += Math.abs(total[i]);
			if(Math.abs(total[i]) > Math.abs(expectedMaximumDeviation)) {
				expectedMaximumDeviation = total[i];
			}
		}
		
		// set missing fields and return solution
		solution.expectedCumulativeDeviation = expectedCumulativeDeviation;
		solution.expectedMaximumDeviation = expectedMaximumDeviation;
		log.info("Finished target schedule optimization.");
		return solutionClass.cast(solution);
	}
}
