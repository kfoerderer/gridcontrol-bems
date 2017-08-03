package de.fzi.osh.com.enabled;

/**
 * Listener for rems signals.
 * 
 * @author Foerderer K.
 *
 */
public interface DataListener {
	
	/**
	 * A coil has changed
	 * 
	 * @param address
	 * @param value
	 */
	public void setCoil(short address, boolean value);
	
	/**
	 * A register has changed.
	 * 
	 * @param address
	 * @param value
	 */
	public void setRegister(short address, short value);
}
