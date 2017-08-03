package de.fzi.osh.data.logging;

import java.time.Instant;

import de.fzi.osh.data.logging.configuration.DataLoggerConfiguration;
import de.fzi.osh.data.logging.types.BatterySocData;
import de.fzi.osh.data.logging.types.MeterData;
import de.fzi.osh.wamp.CommunicationInterface;
import de.fzi.osh.wamp.configuration.WampConfiguration;
import de.fzi.osh.wamp.device.battery.BatterySocPublication;
import de.fzi.osh.wamp.device.battery.BatteryTopics;
import de.fzi.osh.wamp.device.meter.MeterStatePublication;
import de.fzi.osh.wamp.device.meter.MeterTopics;
import ws.wamp.jawampa.SubscriptionFlags;

public class DataLoggerCommunication extends CommunicationInterface<DataLogger, DataLoggerConfiguration>{

	//private static Logger log = Logger.getLogger(DataLoggerCommunication.class.getName());
	private BatteryTopics batteryTopics;
	private MeterTopics meterTopics;
	
	public DataLoggerCommunication(DataLogger component, WampConfiguration wampConfiguration) {
		super(component, wampConfiguration);
		
		batteryTopics = new BatteryTopics(wampConfiguration.topicPrefix);
		meterTopics = new MeterTopics(wampConfiguration.topicPrefix);
		
		connection.onOpen(state -> {
			// subscribe to meter updates via wildcard
			connection.subscribe(meterTopics.meterState(null), SubscriptionFlags.Wildcard, MeterStatePublication.class, parameters -> {
				// generate db entry
				MeterData meterData = new MeterData();		
				
				meterData.uuid = parameters.uuid;
				meterData.alarmFlag = parameters.alarmFlag;
				meterData.totalActiveEnergyN = parameters.totalActiveEnergyN;
				meterData.totalActiveEnergyP = parameters.totalActiveEnergyP;
				meterData.totalActivePower = parameters.totalActivePower;
				meterData.totalReactivePower = parameters.totalReactivePower;
				
				meterData.time = Instant.ofEpochSecond(parameters.time);						
				// write data to storage
				component.getObserver().update(meterData);
			});
			
			// subscribe to soc updates via wildcard
			connection.subscribe(batteryTopics.soc(null), SubscriptionFlags.Wildcard, BatterySocPublication.class, parameters -> {
				// generate db entry
				BatterySocData socData = new BatterySocData();
				socData.uuid = parameters.uuid;
				socData.soc = parameters.soc;

				socData.time = Instant.ofEpochSecond(parameters.time);
				// write data to storage
				component.getObserver().update(socData);
			});
		});
	}
}
