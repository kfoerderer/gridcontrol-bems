package de.fzi.osh.device.battery;

import java.util.logging.Logger;

import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.device.battery.configuration.BatteryConfiguration;
import de.fzi.osh.device.battery.data.BatteryStateData;
import de.fzi.osh.device.time.Time;

public class BatteryController extends de.fzi.osh.core.oc.Controller<Battery, BatteryConfiguration> {
	
	private static Logger log = Logger.getLogger(BatteryController.class.getName());
	
	public int previousStateOfCharge = -1;
	
	public BatteryController(Battery component) {
		super(component);
	}

	public void update(DataObject data) {
		if(data instanceof BatteryStateData) {
			BatteryStateData batteryData = (BatteryStateData) data;
			
			// make the battery follow its schedule
			long now = Time.service().now();

			// negate value for matching sign with direction
			short targetPower = (short) -component.getScheduler().getScheduledPower(now);
			log.finest("Target power: " + targetPower + ".");
			
			// comply to SOC boundaries			
			if(batteryData.stateOfCharge == configuration.maxStateOfCharge && targetPower < 0) {
				// full and charging => don't do that
				component.setRealPower(0);
			}
			else if(batteryData.stateOfCharge == configuration.minStateOfCharge && targetPower > 0) {
				// empty and discharging => don't do that
				component.setRealPower(0);
			}
			else if(Math.abs(batteryData.realPower * 100 - targetPower) >= 100) { // deviation > 100 W
				// try to correct deviation
				component.setRealPower(targetPower);
			}
			
			// check if state of charge changed
			if(previousStateOfCharge != batteryData.stateOfCharge) {
				// soc has changed -> publish soc
				previousStateOfCharge = batteryData.stateOfCharge;
				component.getBusConnection().publishSoc();
			}
		}		
	}
}
