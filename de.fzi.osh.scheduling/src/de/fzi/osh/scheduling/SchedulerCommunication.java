package de.fzi.osh.scheduling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import de.fzi.osh.optimization.schedule.ScheduleData;
import de.fzi.osh.scheduling.configuration.SchedulerConfiguration;
import de.fzi.osh.scheduling.dataobjects.FlexibilitiesChangedData;
import de.fzi.osh.scheduling.dataobjects.MeterMeasurementData;
import de.fzi.osh.types.flexibilities.Flexibility;
import de.fzi.osh.types.flexibilities.SchedulingResult;
import de.fzi.osh.types.flexibilities.Task;
import de.fzi.osh.wamp.Action0;
import de.fzi.osh.wamp.Action1;
import de.fzi.osh.wamp.CommunicationInterface;
import de.fzi.osh.wamp.configuration.WampConfiguration;
import de.fzi.osh.wamp.device.DeviceTopics;
import de.fzi.osh.wamp.device.DriverState;
import de.fzi.osh.wamp.device.SetDriverStateRequest;
import de.fzi.osh.wamp.device.SetDriverStateResponse;
import de.fzi.osh.wamp.device.battery.BatteryTopics;
import de.fzi.osh.wamp.device.battery.GetBatteryStateRequest;
import de.fzi.osh.wamp.device.battery.GetBatteryStateResponse;
import de.fzi.osh.wamp.device.battery.SetTargetSocRequest;
import de.fzi.osh.wamp.device.battery.SetTargetSocResponse;
import de.fzi.osh.wamp.device.meter.MeterStatePublication;
import de.fzi.osh.wamp.device.meter.MeterTopics;
import de.fzi.osh.wamp.rems.ControlRequest;
import de.fzi.osh.wamp.rems.ControlResponse;
import de.fzi.osh.wamp.rems.RemsTopics;
import de.fzi.osh.wamp.schedule.AdaptFlexibilityRequest;
import de.fzi.osh.wamp.schedule.GetFlexibilityRequest;
import de.fzi.osh.wamp.schedule.GetFlexibilityResponse;
import de.fzi.osh.wamp.schedule.GetScheduleRequest;
import de.fzi.osh.wamp.schedule.GetScheduleResponse;
import de.fzi.osh.wamp.schedule.GetTaskRequest;
import de.fzi.osh.wamp.schedule.GetTaskResponse;
import de.fzi.osh.wamp.schedule.ScheduleChangedPublication;
import de.fzi.osh.wamp.schedule.ScheduleFlexibilityRequest;
import de.fzi.osh.wamp.schedule.ScheduleTopics;
import de.fzi.osh.wamp.schedule.SchedulingResponse;
import de.fzi.osh.wamp.schedule.UnscheduleFlexibilityRequest;
import ws.wamp.jawampa.SubscriptionFlags;

public class SchedulerCommunication extends CommunicationInterface<Scheduler, SchedulerConfiguration>{

	private static Logger log = Logger.getLogger(SchedulerCommunication.class.getName());
	
	private DeviceTopics deviceTopics;
	private RemsTopics remsTopics;
	private ScheduleTopics scheduleTopics;
	private MeterTopics meterTopics;
	private BatteryTopics batteryTopics;
	
	public SchedulerCommunication(Scheduler component, WampConfiguration wampConfiguration) {
		super(component, wampConfiguration);
		
		meterTopics = new MeterTopics(wampConfiguration.topicPrefix);
		batteryTopics = new BatteryTopics(wampConfiguration.topicPrefix);
		scheduleTopics = new ScheduleTopics(wampConfiguration.topicPrefix);
		deviceTopics = new DeviceTopics(wampConfiguration.topicPrefix);
		remsTopics = new RemsTopics(wampConfiguration.topicPrefix);
		
		connection.onOpen(state -> {
			// subscribe to meter state via wildcard
			connection.subscribe(meterTopics.meterState(null), SubscriptionFlags.Wildcard, MeterStatePublication.class, parameters -> {
				// create data object
				MeterMeasurementData measurement = new MeterMeasurementData();
				measurement.uuid = parameters.uuid;
				measurement.time = parameters.time;
				measurement.alarmFlag = parameters.alarmFlag;
				measurement.totalActiveEnergyN = parameters.totalActiveEnergyN;
				measurement.totalActiveEnergyP = parameters.totalActiveEnergyP;
				measurement.totalActivePower = parameters.totalActivePower;
				measurement.totalReactivePower = parameters.totalReactivePower;				
				// notify observer
				component.getObserver().update(measurement);
			});
			
			// subscribe to schedule changes via wildcard
			connection.subscribe(scheduleTopics.scheduleChanged(null), SubscriptionFlags.Wildcard, ScheduleChangedPublication.class, parameters -> {
					// create data object
					FlexibilitiesChangedData change = new FlexibilitiesChangedData();
					change.source = parameters.uuid;
					change.from = parameters.from;
					// notify observer
					component.getObserver().update(change);
			});
			
			synchronized (connection) {
				connection.notifyAll();
			}
		});
	}
	
	/**
	 * Waits until a connection is established.
	 */
	public void waitForConnection() {
		synchronized(connection) {
			if(!connection.isConnected()) {
				try {
					log.info("Waiting for WAMP connection.");
					connection.wait();
				} catch (InterruptedException e) {
					log.severe(e.toString());
				}
			}
		}
	}

	/**
	 * Tries to set the driver state of all devices implementing the interface.
	 * 
	 * @param enabled
	 */
	public void setEnabled(boolean enabled) {
		// create request
		SetDriverStateRequest request = new SetDriverStateRequest();
		request.driverState = (enabled == true ? DriverState.On : DriverState.Standby);
		
		// do rpc
		for(UUID uuid : configuration.dnoControllableDevices) {
			connection.call(deviceTopics.setDriverState(uuid), request, SetDriverStateResponse.class, response -> {
						if(true == response.result) {
							log.fine("Setting driver state was successfull.");
						} else {
							log.severe("Setting driver state failed.");
						}
					},
					error -> {
						log.severe("Setting driver state failed.");
						log.severe(error.toString());
						// retry
						Scheduler.getTimeService().schedule(() -> {setEnabled(enabled);}, 10000);
					}, null);
		}
	}
	
	public void getBatteryState(UUID uuid, Action1<GetBatteryStateResponse> onResponse) {
		connection.call(batteryTopics.getBatteryState(uuid), new GetBatteryStateRequest(), GetBatteryStateResponse.class, onResponse, 
				error -> {log.severe("Retrieving battery state failed. [" + error + "]");}, () -> {});
	}
	
	/**
	 * Sets the SOC target for the battery.
	 * 
	 * @param soc
	 * @param time
	 */
	public void setBatteryTarget(UUID uuid, int soc, long time) {
		// create request
		SetTargetSocRequest request = new SetTargetSocRequest();
		request.soc = soc;
		request.time = time;
		
		// do rpc
		connection.call(batteryTopics.setTargetSoc(uuid), request, SetTargetSocResponse.class, response -> {
					log.fine("Told battery " + uuid.toString() + " to reach SOC " + soc + " % at time " + time + ".");
				},
				error -> {
					component.getData().targetChargeData = null;
					component.saveState();
					Scheduler.getControlCommunicationService().publishMessage("Setting target SOC failed." + System.lineSeparator() + error.toString());
					log.severe("Setting target SOC failed.");
					log.severe(error.toString());
				}, null);
	}
	
	/**
	 * Adapts a flexibility.
	 * 
	 * @param uuid
	 * @param flexibilityId
	 * @param power
	 * @param onResponse
	 * @param onError
	 * @param onCompleted
	 */
	public void adaptFlexibility(UUID uuid, int flexibilityId, NavigableMap<Integer, Integer> power, Action1<SchedulingResponse> onResponse, Action1<Throwable> onError, Action0 onCompleted) {
		// create request
		AdaptFlexibilityRequest request = new AdaptFlexibilityRequest();
		request.id = flexibilityId;
		request.power = power;
		
		// DEBUG
		System.out.println("Requesting power " + power.firstEntry().getValue() + " from flexibility " + flexibilityId);
		
		// do rpc
		connection.call(scheduleTopics.adaptFlexibility(uuid), request, SchedulingResponse.class, onResponse, onError, onCompleted);
	}
	
	
	/**
	 * Schedules a flexibility.
	 * 
	 * @param uuid
	 * @param flexibilityId
	 * @param startingTime
	 * @param power
	 * @param onResponse
	 * @param onError
	 * @param onCompleted
	 */
	public void scheduleFlexibility(UUID uuid, int flexibilityId, long startingTime, NavigableMap<Integer, Integer> power, Action1<SchedulingResponse> onResponse, Action1<Throwable> onError, Action0 onCompleted) {
		// create request
		ScheduleFlexibilityRequest request = new ScheduleFlexibilityRequest();
		request.id = flexibilityId;
		request.startingTime = startingTime;
		request.power = power;
		
		// do rpc
		connection.call(scheduleTopics.scheduleFlexibility(uuid), request, SchedulingResponse.class, onResponse, onError, onCompleted);
	}
	
	/**
	 * Unschedules a flexibility.
	 * 
	 * @param uuid
	 * @param flexibilityId
	 * @param onResponse
	 * @param onError
	 * @param onCompleted
	 */
	public void unscheduleFlexibility(UUID uuid, int flexibilityId, Action1<SchedulingResponse> onResponse, Action1<Throwable> onError, Action0 onCompleted) {
		// create request
		UnscheduleFlexibilityRequest request = new UnscheduleFlexibilityRequest();
		request.id = flexibilityId;
		
		// do rpc
		connection.call(scheduleTopics.unscheduleFlexibility(uuid), request, SchedulingResponse.class, onResponse, onError, onCompleted);
	}
	
	/**
	 * Retrieves a the schedule of a device or all devices.
	 * 
	 * @param uuid UUID of target or <i>null</i> for all devices.
	 * @param from
	 * @param to
	 * @param onResponse
	 * @param onError
	 * @param onCompleted
	 */
	public void getSchedule(UUID uuid, long from, long to, Action1<GetScheduleResponse> onResponse, Action1<Throwable> onError, Action0 onCompleted) {
		// create request
		GetScheduleRequest request = new GetScheduleRequest();
		request.from = from;
		request.to = to;
		
		// do rpc
		connection.call(scheduleTopics.getSchedule(uuid), request, GetScheduleResponse.class, onResponse, onError, onCompleted);
	}
	
	/**
	 * Retrieves a flexibility.
	 * 
	 * @param uuid
	 * @param flexibilityId
	 * @param onResponse
	 * @param onError
	 * @param onCompleted
	 */
	public void getFlexibility(UUID uuid, int flexibilityId, Action1<GetFlexibilityResponse> onResponse, Action1<Throwable> onError, Action0 onCompleted) {
		// create request
		GetFlexibilityRequest request = new GetFlexibilityRequest();
		request.id = flexibilityId;
		
		// do rpc
		connection.call(scheduleTopics.getFlexibility(uuid), request, GetFlexibilityResponse.class, onResponse, onError, onCompleted);
	}
	
	/**
	 * Retrieves a task.
	 * 
	 * @param uuid
	 * @param taskId
	 * @param onResponse
	 * @param onError
	 * @param onCompleted
	 */
	public void getTask(UUID uuid, int taskId, Action1<GetTaskResponse> onResponse, Action1<Throwable> onError, Action0 onCompleted) {
		// create request
		GetTaskRequest request = new GetTaskRequest();
		request.id = taskId;
		
		// do rpc
		connection.call(scheduleTopics.getTask(uuid), request, GetTaskResponse.class, onResponse, onError, onCompleted);
	}
	
	/**
	 * Returns the schedules of all reachable devices.
	 * 
	 * @return
	 */
	public Map<UUID, ScheduleData> retrieveSchedules(long from, long to) {
		
		log.fine("Retrieving schedule from '" + from + "' to '" + to + "'.");
		
		CountDownLatch scheduleLatch = new CountDownLatch(configuration.flexibilityProviders.length);
		Map<UUID, GetScheduleResponse> responses = new ConcurrentHashMap<UUID, GetScheduleResponse>();
		
		// first get schedule data
		for(UUID uuid : configuration.flexibilityProviders) {
			this.getSchedule(uuid, from, to, 
					response -> {
						responses.put(response.uuid, response);
						scheduleLatch.countDown();
					}, error -> {
						log.severe("Could not retrieve schedules.");
						scheduleLatch.countDown();
					}, null);
		}
		
		// wait till all requests are finished
		try {
			scheduleLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if(responses.size() == 0) {
			log.warning("No schedules found.");
			return null;
		}
		
		CountDownLatch scheduleDetailLatch = new CountDownLatch(responses.size());
		
		Map<UUID, ScheduleData> schedules = new HashMap<UUID, ScheduleData>();
		
		// now retrieve all flexibilities and tasks
		for(GetScheduleResponse response : responses.values()) {
			
			log.fine("Requesting flexibilities " + Arrays.toString(response.flexibilities) + 
					" and tasks " + Arrays.toString(response.tasks) + " from application '" + response.uuid + "'." );
			// fill in available
			ScheduleData scheduleData = new ScheduleData();
			scheduleData.uuid = response.uuid;
			scheduleData.from = response.from;
			scheduleData.to = response.to;
			scheduleData.constraints = response.constraints;					
			// set data as input
			schedules.put(response.uuid, scheduleData);
			
			CountDownLatch deviceLatch = new CountDownLatch(response.flexibilities.length + response.tasks.length);
			List<Flexibility> flexibilities = new ArrayList<Flexibility>();
			List<Task> tasks = new ArrayList<Task>();
			// request missing data
			for(int id : response.flexibilities) {
				this.getFlexibility(response.uuid, id, flexibility -> {
						flexibilities.add(flexibility.flexibility);
						deviceLatch.countDown();
					}, error -> {
						log.severe("Could not retrieve flexibility '" + id + "'.");
						deviceLatch.countDown();
					}, null);
			}
			for(int id : response.tasks) {
				this.getTask(response.uuid, id, task -> {
						tasks.add(task.task);
						deviceLatch.countDown();
					}, error -> {
						log.severe("Could not retrieve task '" + id + "'.");
						deviceLatch.countDown();
					}, null);
			}
			// wait till all requests for this device are finished
			try {
				deviceLatch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			// fill in missing data
			scheduleData.flexibilities = flexibilities.toArray(new Flexibility[flexibilities.size()]);
			scheduleData.tasks = tasks.toArray(new Task[tasks.size()]);
			
			// finished
			scheduleDetailLatch.countDown();
		}
		
		// wait again for all requests to finish
		try {
			scheduleDetailLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return schedules;
	}
	
	/**
	 * Schedules the given tasks and keeps track of all adaptable tasks.
	 * 
	 * @param tasks solution
	 * @param schedules schedules right now (prior optimization)
	 */
	public void scheduleTasks(Map<UUID, Task[]> tasks, Map<UUID, ScheduleData> schedules) {
		// get references for later access
		Map<UUID, Map<Integer, Task>> adaptableTasks = component.getAdaptableTasks();
		Map<UUID, Map<Integer, Flexibility>> adaptableFlexibilities = component.getAdaptableFlexibilities();
		
		long now = Scheduler.getTimeService().now();
		// remove old tasks		
		for(Iterator<Map.Entry<UUID, Map<Integer, Task>>> deviceIterator = component.getAdaptableTasks().entrySet().iterator(); deviceIterator.hasNext();) {
			Map.Entry<UUID, Map<Integer, Task>> deviceEntry = deviceIterator.next();
			
			Map<Integer, Task> taskMap = deviceEntry.getValue();
			for(Iterator<Map.Entry<Integer, Task>> taskIterator = taskMap.entrySet().iterator(); taskIterator.hasNext();) {
				Map.Entry<Integer, Task> taskEntry = taskIterator.next(); 
				// is it outdated?
				Task task = taskEntry.getValue();
				if(task.startingTime + task.runningTime <= now) {
					taskIterator.remove();
				}
			}
			// are there still tasks for this device?
			if(taskMap.size() == 0) {
				deviceIterator.remove();
			}
		}
		// remove old flexibilities		
		for(Iterator<Map.Entry<UUID, Map<Integer, Flexibility>>> deviceIterator = component.getAdaptableFlexibilities().entrySet().iterator(); deviceIterator.hasNext();) {
			Map.Entry<UUID, Map<Integer, Flexibility>> deviceEntry = deviceIterator.next();
			
			Map<Integer, Flexibility> flexibilityMap = deviceEntry.getValue();
			for(Iterator<Map.Entry<Integer, Flexibility>> flexibilityIterator = flexibilityMap.entrySet().iterator(); flexibilityIterator.hasNext();) {
				Map.Entry<Integer, Flexibility> flexibilityEntry = flexibilityIterator.next(); 
				// is it outdated?
				Flexibility flexibility = flexibilityEntry.getValue();
				if(flexibility.stoppingTime.max <= now) {
					flexibilityIterator.remove();
				}
			}
			// are there still flexibilities for this device?
			if(flexibilityMap.size() == 0) {
				deviceIterator.remove();
			}
		}
		
		
		for(Map.Entry<UUID, Task[]> taskEntry : tasks.entrySet()) {
			
			log.fine("(Re-)Scheduling and adapting flexibilities for application '" + taskEntry.getKey() + "'." );								
			for(Task task :  taskEntry.getValue()) {
				// reschedule flexibilities that have not started yet
				if(task.startingTime > Scheduler.getTimeService().now() + 1) { // add a little buffer for communication
					
					this.scheduleFlexibility(taskEntry.getKey(), task.id, task.startingTime, task.power,
							response -> {
								if(response.result != SchedulingResult.Ok) {
									log.severe("Could not schedule flexibility for application '" + taskEntry.getKey() + "' (" + response.result + ").");
								}
							}, error -> {
								log.severe("Could not schedule flexibility for application '" + taskEntry.getKey() + "'.");
							}, () -> {
							});
				}
				// has already started => adapt schedule
				else if(task.adaptable) {
					
					this.adaptFlexibility(taskEntry.getKey(), task.flexibilityId, task.power,
							response -> {
								if(response.result != SchedulingResult.Ok) {
									log.severe("Could not adapt flexibility for application '" + taskEntry.getKey() + "' (" + response.result + ").");
								}
							}, error -> {
								log.severe("Could not adapt flexibility for application '" + taskEntry.getKey() + "'.");
							}, () -> {
							});
					
				}
				// solution should only hand us tasks that we want to change, so this should not happen
				else {
					log.warning("Task " + task.id + " can neither be (re-)scheduled nor adapted for application '" + taskEntry.getKey() + "'." );
				}
							
				// keep track of adaptable flexibilities (this has to be an extra if-clause) [!]
				if(task.adaptable) {
					// is there a list for this device?
					Map<Integer, Task> taskMap = adaptableTasks.get(taskEntry.getKey());
					if(null == taskMap) {
						// no => create one
						taskMap = new HashMap<Integer, Task>();
						adaptableTasks.put(taskEntry.getKey(), taskMap);
					}
					// add to list
					taskMap.put(task.id, task);
					
					// same again for flexibility
					Map<Integer, Flexibility> flexibilityMap = adaptableFlexibilities.get(taskEntry.getKey());
					if(null == flexibilityMap) {
						flexibilityMap = new HashMap<Integer, Flexibility>();
						adaptableFlexibilities.put(taskEntry.getKey(), flexibilityMap);
					}
					// find corresponding flexibility
					Flexibility[] flexibilities = schedules.get(taskEntry.getKey()).flexibilities;
					for(Flexibility flexibility: flexibilities) {
						if(flexibility.id == task.flexibilityId) {
							// found
							flexibilityMap.put(task.flexibilityId, flexibility);
							break;
						}
					}
				}							
			}
		}
	}
	
	public void forwardRemsControl(UUID uuid, Map<Short, Boolean> coils, Map<Short, Short> registers) {
		ControlRequest request = new ControlRequest();
		request.coils = coils;
		request.registers = registers;
		
		log.finest("Forwarding data.");
		if(null != coils) {
			log.finest("Coils: " + Arrays.toString(coils.entrySet().toArray()));
		}
		if(null != registers) {
			log.finest("Registers: " + Arrays.toString(registers.entrySet().toArray()));
		}
		
		connection.call(remsTopics.controlSignal(uuid), request, ControlResponse.class, null, null, null);
	}
}
