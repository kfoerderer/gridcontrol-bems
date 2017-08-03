package de.fzi.osh.device.meter;

import java.util.logging.Logger;

import de.fzi.osh.device.CommunicationInterface;
import de.fzi.osh.device.meter.configuration.MeterConfiguration;
import de.fzi.osh.wamp.device.meter.MeterStatePublication;
import de.fzi.osh.wamp.device.meter.MeterTopics;

public class MeterCommunication extends CommunicationInterface<MeteringDevice, MeterConfiguration>{

	private Logger log = Logger.getLogger(MeterCommunication.class.getName());
	
	private MeterTopics meterTopics;
	
	public MeterCommunication(MeteringDevice device) {
		super(device);
		
		meterTopics = new MeterTopics(configuration.wamp.topicPrefix);
		
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

}
