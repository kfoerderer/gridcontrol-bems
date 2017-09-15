package de.fzi.osh.device.battery;

import de.fzi.osh.core.oc.Controller;
import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.device.battery.configuration.BatteryConfiguration;
import de.fzi.osh.device.battery.data.BatteryStateData;

public class BatteryObserver extends de.fzi.osh.core.oc.Observer<Battery, BatteryConfiguration> {
	
	
	public BatteryObserver(Battery component, Controller<Battery, BatteryConfiguration> controller) {
		super(component, controller);	
	}

	public void update(DataObject data) {
		if(data instanceof BatteryStateData) {
			BatteryStateData batteryData = (BatteryStateData) data;
			
			controller.update(batteryData);
		}
	}
}
