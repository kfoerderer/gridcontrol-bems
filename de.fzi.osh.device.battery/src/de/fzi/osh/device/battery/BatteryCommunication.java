package de.fzi.osh.device.battery;

import java.util.Map;
import java.util.logging.Logger;

import de.fzi.osh.device.CommunicationInterface;
import de.fzi.osh.device.battery.configuration.BatteryConfiguration;
import de.fzi.osh.device.battery.data.BatterySchedulerData;
import de.fzi.osh.device.battery.data.BatteryStateData;
import de.fzi.osh.device.time.Time;
import de.fzi.osh.wamp.device.DeviceTopics;
import de.fzi.osh.wamp.device.GetDriverStateRequest;
import de.fzi.osh.wamp.device.GetDriverStateResponse;
import de.fzi.osh.wamp.device.SetDriverStateRequest;
import de.fzi.osh.wamp.device.SetDriverStateResponse;
import de.fzi.osh.wamp.device.battery.BatterySocPublication;
import de.fzi.osh.wamp.device.battery.BatteryTopics;
import de.fzi.osh.wamp.device.battery.GetBatteryStateRequest;
import de.fzi.osh.wamp.device.battery.GetBatteryStateResponse;
import de.fzi.osh.wamp.device.battery.SetRealPowerRequest;
import de.fzi.osh.wamp.device.battery.SetRealPowerResponse;
import de.fzi.osh.wamp.device.battery.SetTargetSocRequest;
import de.fzi.osh.wamp.device.battery.SetTargetSocResponse;
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

/**
 * CommunicationInterface interface.
 * 
 * @author K. Foerderer
 *
 */
public class BatteryCommunication extends CommunicationInterface<Battery, BatteryConfiguration> {

	private static Logger log = Logger.getLogger(BatteryCommunication.class.getName());
	
	private DeviceTopics deviceTopics;
	private RemsTopics remsTopics;
	private ScheduleTopics scheduleTopics;
	private BatteryTopics batteryTopics;
    
	public BatteryCommunication(Battery battery) {
		super(battery);
		
		deviceTopics = new DeviceTopics(configuration.wamp.topicPrefix);
		remsTopics = new RemsTopics(configuration.wamp.topicPrefix);
		scheduleTopics = new ScheduleTopics(configuration.wamp.topicPrefix);
		batteryTopics = new BatteryTopics(configuration.wamp.topicPrefix);
		
		connection.onOpen(state -> {

			/**
			 * DeviceDriver Interface
			 */
			
			// get driver state
			connection.register(deviceTopics.getDriverState(device.getUUID()), GetDriverStateRequest.class, parameters -> {
					// send response
					GetDriverStateResponse response = new GetDriverStateResponse();
					response.driverState = device.getDriverState();
					return response;								
			}, null);
			
			// set driver state
			connection.register(deviceTopics.setDriverState(device.getUUID()), SetDriverStateRequest.class, parameters -> {
				// set state
				SetDriverStateResponse response = new SetDriverStateResponse();
				response.result = device.setDriverState(parameters.driverState);				
				return response;
			}, null);
			
			/**
			 * Rems Interface
			 */
			
			// rems battery control
			connection.register(remsTopics.controlSignal(device.getUUID()), ControlRequest.class, parameters -> {

				// pass data to driver
				battery.setRemsRequest(parameters);
				
				// send response
				ControlResponse response = new ControlResponse();				
				return response;
			}, null);
			
			/**
			 * Battery Interface
			 */
			
			// get battery state
			connection.register(batteryTopics.getBatteryState(device.getUUID()), GetBatteryStateRequest.class, parameters -> {
				GetBatteryStateResponse response = new GetBatteryStateResponse();

				// fill in response data
				BatteryStateData data = device.getCurrentStateData();
				if(null == data) {
					// no data yet
					return null;
				}
				BatterySchedulerData schedulerData = device.getScheduler().getSchedulerData();
				
				response.effectiveStateOfCharge = (100 * (data.stateOfCharge - configuration.minStateOfCharge)) / (configuration.maxStateOfCharge - configuration.minStateOfCharge);
				response.effectiveCapacity = configuration.nominalCapacity * (configuration.maxStateOfCharge - configuration.minStateOfCharge) / 100;
				
				response.nominalCapacity = configuration.nominalCapacity;
				response.maxRealPowerCharge = data.maxRealPowerCharge;
				response.maxRealPowerDischarge = data.maxRealPowerDischarge;
				response.realPower = data.realPower * 100;				
				response.stateOfCharge = (byte)data.stateOfCharge;
				response.stateOfHealth = (byte)data.stateOfHealth;
				response.targetSoc = schedulerData.targetSOC;
				response.targetSocTime = schedulerData.targetSocTime;
				response.systemState = data.systemState.toString();
				response.systemStateCode = (short)data.systemState.getValue();
				response.energyUntilEmpty = data.energyUntilEmpty;
				response.energyUntilFull = data.energyUntilFull;
				
				response.systemErrorCode = new short[4];
				response.systemErrorCode[0] = data.systemErrorCode[0];
				response.systemErrorCode[1] = data.systemErrorCode[1];
				response.systemErrorCode[2] = data.systemErrorCode[2];
				response.systemErrorCode[3] = data.systemErrorCode[3];
				
				return response;
			}, null);

			// set real power
			connection.register(batteryTopics.setRealPower(device.getUUID()), SetRealPowerRequest.class, parameters -> {
				// set real power
				device.getScheduler().schedulePower(Time.service().now(), parameters.realPower);
				
				// send response
				return new SetRealPowerResponse();	
			}, null);

			// set target soc
			connection.register(batteryTopics.setTargetSoc(device.getUUID()), SetTargetSocRequest.class, parameters -> {
				// set target soc
				device.getScheduler().setTargetSoc(parameters.soc, parameters.time);
				// send response
				return new SetTargetSocResponse();				
			}, null);
			
			/**
			 * Schedule Interface
			 */
			
			// get schedule			
			connection.register(scheduleTopics.getSchedule(device.getUUID()), GetScheduleRequest.class, parameters -> {
				// retrieve schedule
				GetScheduleResponse response = device.getScheduler().getSchedule(parameters.from, parameters.to);
				response.uuid = device.getUUID();
				return response;
			}, null);
			
			// get flexibility
			connection.register(scheduleTopics.getFlexibility(device.getUUID()), GetFlexibilityRequest.class, parameters -> {
				// retrieve flexibility
				GetFlexibilityResponse response = new GetFlexibilityResponse();
				response.flexibility = device.getScheduler().getFlexibility(parameters.id);
				return response;				
			}, null);
			
			// get task
			connection.register(scheduleTopics.getTask(device.getUUID()), GetTaskRequest.class, parameters -> {
				// retrieve flexibility
				GetTaskResponse response = new GetTaskResponse();
				response.task = device.getScheduler().getTask(parameters.id);
				return response;
			}, null);
			
			// adapt flexibility
			connection.register(scheduleTopics.adaptFlexibility(device.getUUID()), AdaptFlexibilityRequest.class, parameters -> {
				String power = "{";
				for(Map.Entry<Integer, Integer> entry : parameters.power.entrySet()) {
					power += entry.getKey() + ":" + entry.getValue() + ", ";
				}
				power = power.substring(0, power.length() - 2) +  "}";
				log.finest("[RPC] Adapt flexibility '" + parameters.id + "' with power=" + power + ".");
				// try adaptation
				SchedulingResponse response = new SchedulingResponse();
				response.result = device.getScheduler().adaptScheduledFlexibility(parameters.id, parameters.power);
				return response;
			}, null);
			
			// schedule flexibility
			connection.register(scheduleTopics.scheduleFlexibility(device.getUUID()), ScheduleFlexibilityRequest.class, parameters -> {
				// try adaptation
				SchedulingResponse response = new SchedulingResponse();
				response.result = device.getScheduler().scheduleFlexibility(parameters.id, parameters.startingTime, parameters.power);
				return response;
			}, null);
			
			// unschedule flexibility
			connection.register(scheduleTopics.unscheduleFlexibility(device.getUUID()), UnscheduleFlexibilityRequest.class, parameters -> {
				// try adaptation
				SchedulingResponse response = new SchedulingResponse();
				response.result = device.getScheduler().unscheduleFlexibility(parameters.id);
				return response;
			}, null);
			
			// schedule changed
			publishScheduleChanged(Time.service().now());
		});
	}
	
	public void publishScheduleChanged(long time) {
		if(connection.isConnected()) {
			ScheduleChangedPublication publication = new ScheduleChangedPublication();
			publication.from = time;
			publication.uuid = device.getUUID();
			connection.publish(scheduleTopics.scheduleChanged(device.getUUID()), publication, null, null, null);
		}
	}
	
	public void publishSoc() {
		if(connection.isConnected()) {
			BatteryStateData data = device.getCurrentStateData();
			
			BatterySocPublication publication = new BatterySocPublication();
			publication.uuid = device.getUUID();
			publication.time = Time.service().now();
			publication.soc = (byte)data.stateOfCharge;
			publication.effectiveSoc = (byte)((100 * (data.stateOfCharge - configuration.minStateOfCharge)) / (configuration.maxStateOfCharge - configuration.minStateOfCharge));
			
			publication.energyUntilEmpty = data.energyUntilEmpty;
			publication.energyUntilFull = data.energyUntilFull;
			connection.publish(batteryTopics.soc(device.getUUID()), publication, null, null, null);
		}
	}
}
