package de.fzi.osh.device.battery;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import de.fzi.osh.core.data.Json;
import de.fzi.osh.device.battery.configuration.BatteryConfiguration;
import de.fzi.osh.device.battery.data.BatteryStateData;
import de.fzi.osh.device.time.Time;
import de.fzi.osh.device.battery.data.BatterySchedulerData;
import de.fzi.osh.types.flexibilities.Flexibility;
import de.fzi.osh.types.flexibilities.SchedulingResult;
import de.fzi.osh.types.flexibilities.Task;
import de.fzi.osh.types.math.IntInterval;
import de.fzi.osh.wamp.schedule.GetScheduleResponse;

/**
 * Schedules battery operations. The battery is permanently running and automatically creates a flexibility and a task for each day.
 * Updates are issued through adaptation.
 * 
 * 
 * flex + task					flex + task									flex + task
 * ------------|----------------------------------------------|-------------------------------
 * 			next day									next day + 1											
 * 
 * Every time the flexibilities are polled, they get updated to reflect the current state better. To ensure, that adaptations do not get blocked by
 * previous changes to the flexibilities, the power and energy corridor for past values are removed.
 * 
 * @author K. Foerderer
 *
 */
public class BatteryScheduler implements Runnable {

	private static Logger log = Logger.getLogger(BatteryScheduler.class.getName());

	private BatteryConfiguration configuration;
	private Battery battery;

	/**
	 * Holds all the data needed for operation
	 */
	private volatile BatterySchedulerData data;
	
	/**
	 * Name of file used to store the scheduler data
	 */
	private String schedulerDataFile;
	
	/**
	 * Constructor
	 * 
	 * @param battery
	 */
	public BatteryScheduler(Battery battery) {
		this.battery = battery;
		this.configuration = battery.getConfiguration();
		
		// load scheduler data if available
		schedulerDataFile = battery.getConfiguration().schedulerDataFile;
		log.fine("Loading persistence data from file " + schedulerDataFile );
		restoreState(schedulerDataFile);
	}
	
	/**
	 * Returns the scheduler data.
	 * 
	 * @return
	 */
	public BatterySchedulerData getSchedulerData() {
		return data;
	}
	
	/**
	 * Loads the scheduler data from a file.
	 */
	private void restoreState(String filename) {
		data = Json.readFile(filename, BatterySchedulerData.class);
		if(data == null) {
			log.warning("Scheduler data could not be restored correctly.");
			data = new BatterySchedulerData();
		}
	}
	
	/**
	 * Writes the scheduler data to a file.
	 */
	private synchronized void saveState(String filename) {
		Json.writeFile(filename, data);
	}
	
	/**
	 * Helper method that creates a flexibility and the corresponding task covering the day given through the time parameter (floored).
	 * 
	 * @param time
	 */
	private int addFlexibilityForDay(long time) {
		if(getTaskIdForTime(time, false) >= 0) {
			// there already is a flexibility
			return getTaskIdForTime(time, false);
		}
		
		// use zoned date time to accommodate for daylight savings
		ZonedDateTime day = ZonedDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.systemDefault());
		day = day.truncatedTo(ChronoUnit.DAYS);
		
		// create flexibility
		Flexibility flexibility = new Flexibility();
		
		// set id to a new value
		flexibility.id = data.idCounter++;
		
		// newly created flexibility
		flexibility.taskId = flexibility.id;
		
		// flexibility is adaptable
		flexibility.adaptable = true;
		
		// the flexibility start on 00:00 and runs 1d
		ZonedDateTime nextDay = day.plus(1, ChronoUnit.DAYS);
		flexibility.stoppingTime.min = nextDay.toEpochSecond();
		flexibility.stoppingTime.max = flexibility.stoppingTime.min;
		
		flexibility.runningTime.max = (int)(nextDay.toEpochSecond() - day.toEpochSecond());
		flexibility.runningTime.min = flexibility.runningTime.max;
		
		// offer power flexibility within the bounds specified in the configuration
		flexibility.powerCorridor.put(0, new IntInterval(-configuration.maxFlexibilityDischarge, configuration.maxFlexibilityCharge));
		
		// offer the remaining stored energy / remaining free storage space according to current schedule
		int storedEnergy = getExpectedStoredEnergy(flexibility.stoppingTime.min - flexibility.runningTime.max);
		int availableEnergy = storedEnergy - configuration.minStateOfCharge * configuration.nominalCapacity / 100 - configuration.flexibilityEnergyBuffer;
		int availableStorage = configuration.maxStateOfCharge * configuration.nominalCapacity / 100 - storedEnergy - configuration.flexibilityEnergyBuffer;
		flexibility.energyCorridor.put(0, new IntInterval(-availableEnergy * 60 * 60, availableStorage * 60 * 60));

		// create a task with the same id
		Task task = new Task();
		task.adaptable = true;
		task.flexibilityId = flexibility.id;
		task.id = flexibility.taskId;
		task.power = new TreeMap<Integer, Integer>();
		task.power.put(0, 0);
		task.runningTime = flexibility.runningTime.max;
		task.startingTime = flexibility.stoppingTime.min - task.runningTime;
		
		synchronized (this) {
			// actual scheduling
			data.scheduledPower.put(task.startingTime, 0);
		
			// save data
			data.flexibilities.put(flexibility.id, flexibility);
			data.tasks.put(task.id, task);
						
			//persistence
			saveState(schedulerDataFile);	
		}
		return task.id;
	}
	
	/**
	 * Returns the expected amount of stored energy for a given time in the future
	 * 
	 * @param time
	 * @return
	 */
	public synchronized int getExpectedStoredEnergy(long time) {
		int storedEnergy = battery.getCurrentStateData().stateOfCharge * configuration.nominalCapacity / 100;
		if(time <= Time.service().now()) {
			return storedEnergy;
		}
		
		NavigableMap<Long, Integer> schedule = data.scheduledPower;
		long t = Time.service().now();
		Long nextTime;
		while((nextTime = schedule.higherKey(t)) != null) {
			int power = schedule.floorEntry(t).getValue();
			
			// delta Energy [Wh] = delta Time [s] / (60 * 60) [s/h] * -power [W] 
			storedEnergy += (int)((nextTime - t) * -power / (60.0 * 60));
			
			// do time step
			t = nextTime;
		}
		
		return storedEnergy;
	}
	
	/**
	 * Returns all current flexibilities
	 * @see de.fzi.osh.alljoyn.interfaces.Flexibilities#getFlexibilities(long)
	 * 
	 * @param id
	 * @param power
	 * @return
	 * @throws BusException
	 */
	public GetScheduleResponse getSchedule(long from, long to) {		
		// < 48 hours
		if(to - from > 2 * 24 * 60 * 60) {
			return null;
		}
		
		// get flexibilities or create them if they are new
		int fromTaskId = getTaskIdForTime(from, false);
		if(fromTaskId < 0) {
			fromTaskId = addFlexibilityForDay(from);
		}
		
		int toTaskId = getTaskIdForTime(to, true);
		if(toTaskId < 0) {
			toTaskId = addFlexibilityForDay(to);
		}
		
		// package flexibility and task data and adapt them to reflect the current state
		Flexibility[] flexibilities;
		Task[] tasks;
		if(fromTaskId == toTaskId) {
			// same day
			flexibilities = new Flexibility[1];
			tasks = new Task[1];
			tasks[0] = data.tasks.get(fromTaskId);
			flexibilities[0] = data.flexibilities.get(tasks[0].flexibilityId);
			
			// remove old limitations and set new ones
			long now = Time.service().now();
			int relativeNow = (int)(now - tasks[0].startingTime);
			
			// offer power flexibility within the bounds specified in the configuration
			flexibilities[0].powerCorridor = new TreeMap<Integer, IntInterval>();
			// deal with target soc settings
			if(now < data.targetSocTime && data.targetSOC > 0) {
				relativeNow = (int)(data.targetSocTime - tasks[0].startingTime);
				flexibilities[0].powerCorridor.put(0, new IntInterval(0, 0));
			} else {
				flexibilities[0].powerCorridor.put(0, new IntInterval(0, 0));
			}
			flexibilities[0].powerCorridor.put(relativeNow < 0 ? 0 : relativeNow, new IntInterval(-configuration.maxFlexibilityDischarge, configuration.maxFlexibilityCharge));
			
			// offer the remaining stored energy / remaining free storage space according to current schedule
			int storedEnergy = getExpectedStoredEnergy(now);
			int availableEnergy = storedEnergy - configuration.minStateOfCharge * configuration.nominalCapacity / 100 - configuration.flexibilityEnergyBuffer;
			int availableStorage = configuration.maxStateOfCharge * configuration.nominalCapacity / 100 - storedEnergy - configuration.flexibilityEnergyBuffer;
			
			flexibilities[0].energyCorridor = new TreeMap<Integer, IntInterval>();
			flexibilities[0].energyCorridor.put(0, new IntInterval(Integer.MIN_VALUE, Integer.MAX_VALUE));
			flexibilities[0].energyCorridor.put(relativeNow-1 < 0 ? 0 : relativeNow-1, new IntInterval(Integer.MIN_VALUE, Integer.MAX_VALUE));
			flexibilities[0].energyCorridor.put(relativeNow < 0 ? 0 : relativeNow, new IntInterval(Math.min(0, -availableEnergy * 60 * 60), Math.max(0, availableStorage * 60 * 60)));
		} else {
			// different days
			flexibilities = new Flexibility[2];
			tasks = new Task[2];
			tasks[0] = data.tasks.get(fromTaskId);
			flexibilities[0] = data.flexibilities.get(tasks[0].flexibilityId);
			tasks[1] = data.tasks.get(toTaskId);
			flexibilities[1] = data.flexibilities.get(tasks[1].flexibilityId);

			// remove old limitations and set new ones
			long now = Time.service().now();
			int relativeNow = (int)(now - tasks[0].startingTime);
			
			// offer power flexibility within the bounds specified in the configuration
			flexibilities[0].powerCorridor = new TreeMap<Integer, IntInterval>();
			// deal with target soc settings
			if(now < data.targetSocTime && data.targetSOC > 0) {
				relativeNow = (int)(data.targetSocTime - tasks[0].startingTime);
				flexibilities[0].powerCorridor.put(0, new IntInterval(0, 0));
			} else {
				flexibilities[0].powerCorridor.put(0, new IntInterval(0, 0));
			}
			flexibilities[0].powerCorridor.put(relativeNow < 0 ? 0 : relativeNow, new IntInterval(-configuration.maxFlexibilityDischarge, configuration.maxFlexibilityCharge));
			
			// offer the remaining stored energy / remaining free storage space according to current schedule
			int storedEnergy = getExpectedStoredEnergy(now);
			int availableEnergy = storedEnergy - configuration.minStateOfCharge * configuration.nominalCapacity / 100 - configuration.flexibilityEnergyBuffer;
			int availableStorage = configuration.maxStateOfCharge * configuration.nominalCapacity / 100 - storedEnergy - configuration.flexibilityEnergyBuffer;
			
			flexibilities[0].energyCorridor = new TreeMap<Integer, IntInterval>();
			flexibilities[0].energyCorridor.put(0, new IntInterval(Integer.MIN_VALUE, Integer.MAX_VALUE));
			flexibilities[0].energyCorridor.put(relativeNow-1 < 0 ? 0 : relativeNow-1, new IntInterval(Integer.MIN_VALUE, Integer.MAX_VALUE));
			flexibilities[0].energyCorridor.put(relativeNow < 0 ? 0 : relativeNow, new IntInterval(Math.min(0, -availableEnergy * 60 * 60), Math.max(0, availableStorage * 60 * 60)));

			// deal with target soc settings
			if(tasks[1].startingTime < data.targetSocTime && data.targetSOC > 0) {
				relativeNow = (int)(data.targetSocTime - tasks[1].startingTime);
				flexibilities[1].powerCorridor.put(0, new IntInterval(0, 0));
				flexibilities[1].powerCorridor.put(relativeNow, new IntInterval(-configuration.maxFlexibilityDischarge, configuration.maxFlexibilityCharge));
			} else {
				flexibilities[1].powerCorridor.put(0, new IntInterval(-configuration.maxFlexibilityDischarge, configuration.maxFlexibilityCharge));	
			}
			
			// flexibilities[1] is always in the future, hence only adapt energy restrictions
			storedEnergy = getExpectedStoredEnergy(tasks[1].startingTime);
			availableEnergy = storedEnergy - configuration.minStateOfCharge * configuration.nominalCapacity / 100 - configuration.flexibilityEnergyBuffer;
			availableStorage = configuration.maxStateOfCharge * configuration.nominalCapacity / 100 - storedEnergy - configuration.flexibilityEnergyBuffer;
			
			flexibilities[1].energyCorridor.put(0, new IntInterval(-availableEnergy * 60 * 60, availableStorage * 60 * 60));
		}
	
		
		// package it into a bundle
		GetScheduleResponse data = new GetScheduleResponse();
		data.from = Time.service().now();
		data.to = to;
		data.constraints = new String[] {""};
		
		int[] flexibilityIds = new int[flexibilities.length];
		int i = 0;
		for(Flexibility f : flexibilities) {
			flexibilityIds[i] = f.id;
			i++;
		}
		data.flexibilities = flexibilityIds;
		
		int[] taskIds = new int[tasks.length];
		i = 0;
		for(Task t : tasks) {
			taskIds[i] = t.id;
			i++;
		}		
		data.tasks = taskIds;
		
		// publish
		return data;
	}

	/**
	 * Returns the flexibility with the given id.
	 * @see de.fzi.osh.alljoyn.interfaces.Flexibilities#getFlexibility(int)
	 * 
	 * @param id
	 * @return
	 */
	public Flexibility getFlexibility(int id) {
		return data.flexibilities.get(id);
	}
	
	/**
	 * Returns the task with the given id
	 * 
	 * @param id
	 * @return
	 */
	public Task getTask(int id) {
		return data.tasks.get(id);
	}
	
	/**
	 * Adapts the schedule for an adaptable scheduled flexibility.
	 * 
	 * @param id
	 * @param power
	 * @return
	 */
	public SchedulingResult adaptScheduledFlexibility(int id, NavigableMap<Integer, Integer> powers) {
		// get flexibility data
		Flexibility flexibility = data.flexibilities.get(id);
		if(flexibility == null) {
			log.warning("Unknown.");
			return SchedulingResult.UnknownFlexibility;
		}
		
		// only adaptable flexibilities may be adapted
		if(false == flexibility.adaptable) {
			log.warning("Illegal.");
			return SchedulingResult.Illegal;
		}
		
		// check if this is a valid update
		// therefore do a temporary schedule update
		Task task = data.tasks.get(flexibility.taskId);
		NavigableMap<Integer, Integer> updatedPowers = new TreeMap<Integer, Integer>(task.power);
		
		// debug
		/*String msg = "{";
		for(Map.Entry<Integer, Integer> entry : updatedPowers.entrySet()) {
			msg += entry.getKey() + ":" + entry.getValue() + ", ";
		}
		msg = msg.substring(0, msg.length() - 2) + "}";
		log.info(msg);*/
		
		// the last key only marks the end of the update
		powers.put(powers.lastKey() , updatedPowers.floorEntry(powers.lastKey()).getValue());
		
		// delete values that get overwritten
		NavigableMap<Integer, Integer> taskPowersSubMap = updatedPowers.subMap(powers.firstKey(), true, powers.lastKey(), true);
		for(Iterator<Map.Entry<Integer, Integer>> iterator = taskPowersSubMap.entrySet().iterator(); iterator.hasNext(); ) {
			iterator.next();
			iterator.remove();
		}
		// write new data
		for(Map.Entry<Integer, Integer> entry : powers.entrySet()) {
			updatedPowers.put(entry.getKey(), entry.getValue());
		}
		
		// check if the adapted flexibility is still valid
		if(false == flexibility.checkValidity(task.startingTime, updatedPowers)) {
			log.warning("Invalid data.");
			return SchedulingResult.InvalidData;
		}
		// valid update
		
		synchronized (this) {
			// do the update
			task.power = updatedPowers;
			
			// remove old power data
			for(Iterator<Map.Entry<Long, Integer>> iterator = data.scheduledPower.subMap(task.startingTime + powers.firstKey(), true, task.startingTime + powers.lastKey(), true).entrySet().iterator(); iterator.hasNext();) {
				iterator.next();
				iterator.remove();
			}		
			// write new power data
			for(Map.Entry<Integer, Integer> entry : powers.entrySet()) {
				data.scheduledPower.put(task.startingTime + entry.getKey(), entry.getValue());
			}
			
			// compress data
			task.compress();
						
			// no persistence for short term adaptations needed
		}
		
		return SchedulingResult.Ok;
	}
	
	/**
	 * Schedules a flexibility.
	 * @see de.fzi.osh.alljoyn.interfaces.Flexibilities#scheduleFlexibility(int, Map)
	 * 
	 * @param id
	 * @param power
	 * @return
	 * @throws BusException
	 */
	public SchedulingResult scheduleFlexibility(int id, long startingTime, NavigableMap<Integer, Integer> powers) {
		// get flexibility data
		Flexibility flexibility = data.flexibilities.get(id);
		if(flexibility == null) {
			return SchedulingResult.UnknownFlexibility;
		}
		
		// has it started?
		Task task = data.tasks.get(flexibility.taskId);
		if(task.startingTime < Time.service().now()) {
			// already running => use adapt
			return SchedulingResult.Illegal;
		}
		
		// do the power fit the flexibility
		if(flexibility.checkValidity(startingTime, powers) == false) {
			String flexString = "{";
			for(Map.Entry<Integer, Integer> entry : powers.entrySet()) {
				flexString += entry.getKey() + ": " + entry.getValue() + ", ";
			}
			flexString = flexString.substring(0, flexString.length() - 2) + "}";
			log.info("Received invalid flexibility schedule:");
			log.info(flexString);
			return SchedulingResult.InvalidData;
		}
		
		// adapt task
		task.power = powers;
		// all other data doesn't change, since there is no flexibility in those parameters
		
		/* NOT NEEDED:
		// check for conflicts with other tasks
		//
		// [!] keep in mind that a schedule could include periods with power = 0
		//
		for(Task scheduledTask : data.tasks.values()) {			
			// [a,b] intersects [x,y] <=> x <= b AND y >= a 
			// here [a,b) and [x,y)
			if(	scheduledTask.startingTime < task.startingTime + power.lastKey() && 
				scheduledTask.startingTime + scheduledTask.runningTime > task.startingTime) {
				
				return SchedulingResult.Conflict.getValue();
			}
		}*/
		
		// compress data
		task.compress();

		// actual scheduling
		synchronized (this) {
			// unschedule old power values
			for(Iterator<Map.Entry<Long, Integer>> iterator = data.scheduledPower.subMap(task.startingTime, true, task.startingTime + task.runningTime, true).entrySet().iterator();iterator.hasNext();) {
				iterator.next();
				iterator.remove();
			}
			// schedule new power values
			for(Map.Entry<Integer, Integer> entry : powers.entrySet()) {
				data.scheduledPower.put(entry.getKey() + startingTime, entry.getValue());
			}
			//persistence
			saveState(schedulerDataFile);	
		}
		
		log.info("Flexibility '" + id + "' has been scheduled.");
		return SchedulingResult.Ok;
	}

	/**
	 * Removes the task corresponding to the given flexibility
	 * @see de.fzi.osh.alljoyn.interfaces.Flexibilities#unscheduleFlexibility(int)
	 * 
	 * @param id
	 * @return
	 */
	public synchronized SchedulingResult unscheduleFlexibility(int id) {
		Task task = data.tasks.get(id);
		
		if(task == null) {
			return SchedulingResult.UnknownTask;
		}
		
		// can only unschedule tasks that have not started yet => use adaptability to make changes [!]
		if(task.startingTime < Time.service().now()) {
			return SchedulingResult.Illegal;
		}
				
		synchronized (this) {
		
			// remove scheduled power data
			NavigableMap<Long, Integer> scheduledPowerSubMap = data.scheduledPower.subMap(task.startingTime, true, task.startingTime + task.runningTime, true);
			for(Iterator<Map.Entry<Long, Integer>> iterator = scheduledPowerSubMap.entrySet().iterator();iterator.hasNext();) {
				iterator.next();
				iterator.remove();
			}
			
			// reset task data
			task.power.clear();
			task.power.put(0,0);
			
			// task is not removed, since battery will still run and just do nothing
			
			// persistence
			saveState(schedulerDataFile);
		}
		
		return SchedulingResult.Ok;
	}

	/**
	 * Returns all scheduled flexibilities' ids
	 * @see de.fzi.osh.alljoyn.interfaces.Flexibilities#getScheduledFlexibilities()
	 * 
	 * @return
	 */
	public Task[] getScheduledFlexibilities() {
		return data.tasks.values().toArray(new Task[data.tasks.size()]);
	}
	
	/**
	 * Returns the power scheduled for the given time
	 * 
	 * @param epochSeconds
	 * @return
	 */
	public synchronized int getScheduledPower(long epochSeconds) {
		return data.scheduledPower.floorEntry(epochSeconds).getValue();
	}
	
	/**
	 * Checks whether the schedule can be held or not and adapts it if necessary.
	 * 
	 * @return epoch second count of the earliest inconsistency. 0 if everything seems fine.
	 */
	public long fixSchedule(int storedEnergy, int minEnergy, int maxEnergy, NavigableMap<Long, Integer> schedule) {		
		// follow scheduled load profile and evaluate consumption
		long result = 0;
		
		long time = Time.service().now();
		Long nextTime;
		while((nextTime = schedule.higherKey(time)) != null) {
			int power = schedule.floorEntry(time).getValue();
			
			// delta Energy [Wh] = delta Time [s] / (60 * 60) [s/h] * -power [W] 
			int tmp = storedEnergy;
			storedEnergy += (int)((nextTime - time) * power / (60.0 * 60));
			
			if( storedEnergy > maxEnergy && power > 0 || 
				storedEnergy < minEnergy && power < 0) {
				
				// illegal state [!], make an adaptation
				Map.Entry<Long, Integer> entry = schedule.floorEntry(time);
				if(entry == null) {
					schedule.put(time, 0); // setting 0 is always valid
				} else {
					schedule.put(entry.getKey(), 0);
				}
				// now change the task to reflect this change
				int id = getTaskIdForTime(time, false);
				Task task = data.tasks.get(id);
				if(null == task) {
					log.warning("Task " + id + " was not found."); 
				} else {
					NavigableMap<Integer, Integer> taskPower = new TreeMap<Integer, Integer>(task.power);
					Map.Entry<Integer, Integer> taskEntry = taskPower.floorEntry((int)(time - task.startingTime));
					if(null == taskEntry) {
						task.power.put((int)(time - task.startingTime), 0);
					} else {
						task.power.put(taskEntry.getKey(), 0);
					}
				}
				
				// undo the last step
				// delta Energy [Wh] = delta Time [s] / (60 * 60) [s/h] * -power [W] 
				storedEnergy = tmp;
				
				// resulting stored energy is not an acceptable state
				if(0 == result) {
					result = time;				
				}
			}
			
			// do time step
			time = nextTime;
		}
	
		// compress current task
		Task task = data.tasks.get(getTaskIdForTime(Time.service().now(), false));
		if(null != task) {
			task.compress();
		}
		
		return result;
	}
	
	/**
	 * Returns the id of the task running at a given time
	 * 
	 * @param epochSecond
	 * @return The tasks's id or -1, if no runnable was found
	 */
	public int getTaskIdForTime(long epochSecond, boolean endInclusive) {
		for(Task task : data.tasks.values()) {
			if(false == endInclusive) {
				if(task.startingTime <= epochSecond && epochSecond < task.startingTime + task.runningTime) {
					return task.id;
				}
			} else {
				if(task.startingTime <= epochSecond && epochSecond <= task.startingTime + task.runningTime) {
					return task.id;
				}
			}
		}
		return -1;
	}
	
	/**
	 * Background tasks like cleaning up, executed from time to time
	 */
	public void run() {
		try {			
			// get current epoch seconds
			long now = 0;
			
			// clean up old schedule data
			synchronized (this) {
				now = Time.service().now();
				
				// clean up all finished tasks
				Iterator<Map.Entry<Integer, Task>> iterator = data.tasks.entrySet().iterator();
				while(iterator.hasNext()) {
					Task task = iterator.next().getValue();
					if(now > task.startingTime + task.runningTime) {
						// remove task and flex
						log.finest("Removing old task and corresponding flexibility: " + task.id);
						data.flexibilities.remove(task.flexibilityId);
						iterator.remove();
					}
				}
	
				Iterator<Map.Entry<Long, Integer>> scheduleIterator = data.scheduledPower.entrySet().iterator();
				while(scheduleIterator.hasNext()) {
					Map.Entry<Long, Integer> entry = scheduleIterator.next();
					// do not remove instantly if it is in the past for debugging purposes
					if(entry.getKey() != 0 && entry.getKey() + 24 * 60 * 60 < now) {
						// entry is older than 24 hours => remove
						scheduleIterator.remove();
					}
				}
			}
			
			if(data.targetSocTime > now && data.targetSOC > 0) {
				
				// trying to achieve target soc
				setTargetSoc(data.targetSOC, data.targetSocTime);				
			} else {
				
				// usual business
				synchronized(this) {
					
					// retrieve relevant data
					BatteryStateData currentState = battery.getCurrentStateData();
					if(null != currentState) {
						BatteryConfiguration configuration = battery.getConfiguration();
						
						// calculate necessary values
						int storedEnergy = currentState.stateOfCharge * configuration.nominalCapacity / 100;
						int minEnergy = configuration.minStateOfCharge * configuration.nominalCapacity / 100;
						int maxEnergy = configuration.maxStateOfCharge * configuration.nominalCapacity / 100;
						
						// do a plausibility check once in a while and make adaptations if needed
						long time = fixSchedule(storedEnergy, minEnergy, maxEnergy, data.scheduledPower);
	
						//persistence
						saveState(schedulerDataFile);	
	
						if(time > 0) {
							// adaptations have been made !
							int id = getTaskIdForTime(time, true);						
							if(id < 0) { // this should never happen. Nevertheless, check for debugging.
								log.severe("Scheduled power without scheduled task!");
							}
							battery.publishScheduleChanged(time);
							log.info("Changed schedule.");
						}
					}
				}				
			}
		} catch(Exception e) {
			e.printStackTrace();
			log.severe("Scheduler loop:" + e.toString());
		}
	}

	/**
	 * Change schedule to reach target till time
	 * 
	 * @param wh
	 * @param time
	 */
	public synchronized void setTargetSoc(int soc, long time) {
		data.targetSOC = soc;
		data.targetSocTime = time;
		
		long t = Time.service().now();
		
		int duration = (int)(time - t);
		// [Ws]
		int changeInCharge = (soc - battery.getCurrentStateData().stateOfCharge) * configuration.nominalCapacity * 60 * 60 / 100;
		
		// delete everything between now and $time
		for(Iterator<Map.Entry<Long, Integer>> iterator = data.scheduledPower.subMap(t, time).entrySet().iterator(); iterator.hasNext(); ){
			iterator.next();
			iterator.remove();
		}
		do {			
			int averagePower = changeInCharge / duration;
			if(averagePower < 0) {
				// loading
				averagePower -= (averagePower % 100) - 100; // floor, to next 100 W step
				averagePower = Math.max(averagePower, -configuration.maxFlexibilityCharge);
			} else {
				// unloading
				averagePower += (averagePower % 100) - 100; // ceil, to next 100 W step
				averagePower = Math.min(averagePower, configuration.maxFlexibilityDischarge);
			}
		
			data.scheduledPower.put(t, averagePower);
			int step = Math.abs(changeInCharge / averagePower) + 1;
			changeInCharge -= averagePower * step;
			duration -= step;
			
			t += step;
		}while(t < time);
		
		// done
		data.scheduledPower.put(time, 0);
		
		// save changes
		saveState(schedulerDataFile);
	}
	
	/**
	 * Schedules a power for a given time.
	 * (Bypasses tasks an flexibilities)
	 * 
	 * @param time
	 * @param power
	 */
	public synchronized void schedulePower(long time, int power) {
		data.scheduledPower.put(time, power);
	}
	
}
