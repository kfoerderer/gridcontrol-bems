package de.fzi.osh.com.enabled;

import java.util.Map;

/**
 * Service for reacting to the "enabled"-signal
 * 
 * @author K. Foerderer
 *
 */
public interface EnabledListenerService {
	/**
	 * Set "enabled"-signal state.
	 * 
	 * @param enabled
	 * @return
	 */
	public void setEnabled(boolean enabled);
	/**
	 * Returns true if the "enabled"-signal is set.
	 * 
	 * @return
	 */
	public boolean isEnabled();
	/**
	 * Adds a listener.
	 * 
	 * @param listener
	 */
	public void addListener(EnabledListener listener);
	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(EnabledListener listener);
	/**
	 * Makes data available for REMS.
	 * 
	 * @param coil data [read only]
	 * @param coil data
	 * @param register data [read only]
	 * @param register data
	 */
	public void publishData(Map<Short, Boolean> readOnlyCoils, Map<Short, Boolean> coils, Map<Short, Short> readOnlyRegisters, Map<Short, Short> registers);
	/**
	 * Adds a data listener.
	 * 
	 * @param listener
	 */
	public void addDataListener(DataListener listener);
	/**
	 * Removes a data listener.
	 * 
	 * @param listener
	 */
	public void removeDataListener(DataListener listener);
}
