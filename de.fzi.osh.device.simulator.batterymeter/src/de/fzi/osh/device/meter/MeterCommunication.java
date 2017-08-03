package de.fzi.osh.device.meter;

import java.util.logging.Logger;

import de.fzi.osh.device.CommunicationInterface;
import de.fzi.osh.device.meter.configuration.MeterConfiguration;
import de.fzi.osh.wamp.Action0;
import de.fzi.osh.wamp.Action1;
import de.fzi.osh.wamp.device.battery.BatteryTopics;
import de.fzi.osh.wamp.device.battery.GetBatteryStateRequest;
import de.fzi.osh.wamp.device.battery.GetBatteryStateResponse;
import de.fzi.osh.wamp.device.meter.MeterStatePublication;
import de.fzi.osh.wamp.device.meter.MeterTopics;

public class MeterCommunication extends CommunicationInterface<MeteringDevice, MeterConfiguration>{

	private Logger log = Logger.getLogger(MeterCommunication.class.getName());
	
	private BatteryTopics batteryTopics;
	private MeterTopics meterTopics;
	
	public MeterCommunication(MeteringDevice device) {
		super(device);
		
		meterTopics = new MeterTopics(configuration.wamp.topicPrefix);
		batteryTopics = new BatteryTopics(configuration.wamp.topicPrefix);
		
		log.info("Topic for state publication: " + meterTopics.meterState(device.getUUID()));
	}
	
	/**
	 * Published meter state. See {@link MeterStatePublication} for parameter details.
	 * 
	 * @param time
	 * @param totalActivePower
	 * @param totalReactivePower
	 * @param totalActiveEnergyP
	 * @param totalActiveEnergyN
	 * @param alarmFlag
	 */
	public void publishMeterState(long time, int totalActivePower, int totalReactivePower, long totalActiveEnergyP, long totalActiveEnergyN, long alarmFlag) {
		if(!connection.isConnected()) {
			return;
		}
		
		// collect parameters
		MeterStatePublication parameters = new MeterStatePublication();
		parameters.uuid = device.getUUID();
		parameters.time = time;
		parameters.totalActivePower = totalActivePower;
		parameters.totalReactivePower = totalReactivePower;
		parameters.totalActiveEnergyP = totalActiveEnergyP;
		parameters.totalActiveEnergyN = totalActiveEnergyN;
		parameters.alarmFlag = alarmFlag;

		// publish
		connection.publish(meterTopics.meterState(device.getUUID()), parameters, null, null, null);
	}
	
	/**
	 * Requests the current battery state of the associated battery.
	 * 
	 * @param action
	 */
	public boolean getBatteryActivePower(Action1<GetBatteryStateResponse> action, Action1<Throwable> error, Action0 completed) {
		if(!connection.isConnected()) {
			return false;
		}
		
		connection.call(batteryTopics.getBatteryState(configuration.batteryUUID), new GetBatteryStateRequest(), GetBatteryStateResponse.class, 
				action, error, completed);
		return true;
	}

}
