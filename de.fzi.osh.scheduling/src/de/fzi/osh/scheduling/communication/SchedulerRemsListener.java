package de.fzi.osh.scheduling.communication;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.fzi.osh.com.enabled.DataListener;
import de.fzi.osh.scheduling.Scheduler;

/**
 * Listener for forwarding rems control signals.
 * 
 * @author Foerderer K.
 *
 */
public class SchedulerRemsListener implements DataListener{

	private Scheduler scheduler;
	
	public SchedulerRemsListener(Scheduler scheduler) {
		this.scheduler = scheduler;
	}
	
	@Override
	public void setCoil(short address, boolean value) {
		Map<Short, Boolean> data = new HashMap<Short, Boolean>();
		data.put(address, value);
				
		if(scheduler.getConfiguration().dnoControllableDevices != null) {
			for(UUID uuid : scheduler.getConfiguration().dnoControllableDevices) {
				scheduler.getCommunicationInterface().forwardRemsControl(uuid, data, null);
			}
		}
	}

	@Override
	public void setRegister(short address, short value) {
		Map<Short, Short> data = new HashMap<Short, Short>();
		data.put(address, value);
		
		if(scheduler.getConfiguration().dnoControllableDevices != null) {
			for(UUID uuid : scheduler.getConfiguration().dnoControllableDevices) {
				scheduler.getCommunicationInterface().forwardRemsControl(uuid, null, data);
			}
		}
	}
}
