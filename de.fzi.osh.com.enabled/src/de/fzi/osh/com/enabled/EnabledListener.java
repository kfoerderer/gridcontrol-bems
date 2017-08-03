package de.fzi.osh.com.enabled;

/**
 * Interface for listening to the "enabled"-signal
 * 
 * @author K. Foerderer
 *
 */
public interface EnabledListener {
	/**
	 * Called when "enable" has been received
	 */
	public void enable();
	/**
	 * Called when no "enable" has been received
	 */
	public void disable();
}
