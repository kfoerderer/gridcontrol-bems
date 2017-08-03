package de.fzi.osh.core.component;

import de.fzi.osh.core.oc.Controller;
import de.fzi.osh.core.oc.Observer;

/**
 * Base class for osh components. The protected parameters are not set automatically. Hence, they may be <i>null</i>.
 * 
 * @author K. Foerderer
 *
 */
public abstract class OshComponent<T extends OshComponent<T, C>, C extends OshComponentConfiguration> implements Runnable{
	
	protected C configuration;
	protected Observer<T, C> observer;
	protected Controller<T, C> controller;
	
	/**
	 * Returns the observer for this component.
	 * 
	 * @return
	 */
	public Observer<T, C> getObserver() {
		return observer;
	}
	
	/**
	 * Returns the controller for this component.
	 * 
	 * @return
	 */
	public Controller<T, C> getController() {
		return controller;
	}
	
	/**
	 * Returns this component's configuration.
	 * 
	 * @return
	 */
	public C getConfiguration() {
		return configuration;
	}
}
