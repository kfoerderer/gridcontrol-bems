package de.fzi.osh.device.meter;

import de.fzi.osh.core.oc.Controller;
import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.device.meter.configuration.MeterConfiguration;

public class MeteringDeviceObserver extends de.fzi.osh.core.oc.Observer<MeteringDevice, MeterConfiguration> {
		
	public MeteringDeviceObserver(MeteringDevice component, Controller<MeteringDevice, MeterConfiguration> controller) {
		super(component, controller);		
	}

	public void update(DataObject data) {
		// nothing to do
		controller.update(data);
	}
}
