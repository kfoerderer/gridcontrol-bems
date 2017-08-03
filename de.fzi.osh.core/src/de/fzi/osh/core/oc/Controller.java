package de.fzi.osh.core.oc;

import de.fzi.osh.core.component.OshComponent;
import de.fzi.osh.core.component.OshComponentConfiguration;

/**
 * 
 * @author K. Foerderer
 *
 */
public class Controller<T extends OshComponent<T, C>, C extends OshComponentConfiguration> {
	
	protected T component;
	protected C configuration;
	
	public Controller(T component) {
		this.component = component;
		this.configuration = component.getConfiguration();
	}
	
	public void initialize() {
		this.configuration = component.getConfiguration();
	}
	
	public void update(DataObject data) {
		
	}
	
	public void shutdown() {
		
	}	
}
