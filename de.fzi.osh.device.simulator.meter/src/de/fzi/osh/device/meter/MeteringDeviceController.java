package de.fzi.osh.device.meter;

import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.device.meter.configuration.MeterConfiguration;
import de.fzi.osh.device.meter.data.SmartMeterData;

public class MeteringDeviceController extends de.fzi.osh.core.oc.Controller<MeteringDevice, MeterConfiguration> {
	
	public MeteringDeviceController(MeteringDevice component) {
		super(component);
	}

	public void update(DataObject data) {
		if(data instanceof SmartMeterData) {
			// publish data
			SmartMeterData meterData = (SmartMeterData) data;
			component.getBusConnection().publishMeterState(meterData.time.getEpochSecond(), 
															meterData.totalActivePower, 
															meterData.totalReactivePower, 
															meterData.totalActiveEnergyP, 
															meterData.totalActiveEnergyN, 
															meterData.alarmFlag);
		}
	}
}
