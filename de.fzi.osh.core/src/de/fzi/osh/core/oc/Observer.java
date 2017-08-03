package de.fzi.osh.core.oc;

import de.fzi.osh.core.component.OshComponent;
import de.fzi.osh.core.component.OshComponentConfiguration;

/**
 * 
 * @author K. Foerderer
 *
 */
public class Observer<T extends OshComponent<T, C>, C extends OshComponentConfiguration> {

	protected T component;
	protected Controller<T, C> controller;
	protected C configuration;
	
	public Observer(T component, Controller<T, C> controller) {
		this.component = component;
		this.controller = controller;
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
