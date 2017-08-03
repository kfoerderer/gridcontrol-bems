package de.fzi.osh.device;

import java.util.UUID;

import de.fzi.osh.core.component.OshComponent;
import de.fzi.osh.device.configuration.Configuration;

/**
 * Base class for all OSH devices.
 * 
 * @author K. Foerderer
 *
 * @param <T>
 * @param <C>
 */
public abstract class Device<T extends OshComponent<T,C>, C extends Configuration> extends OshComponent<T,C>{
		
	public Device(C configuration)
	{
		this.configuration = configuration;
	}
	
	public void shutdown() {
	}
	
	public UUID getUUID() {
		return configuration.uuid;
	}
}
