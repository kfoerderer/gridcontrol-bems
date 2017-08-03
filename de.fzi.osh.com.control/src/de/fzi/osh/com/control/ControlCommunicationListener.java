package de.fzi.osh.com.control;

/**
 * Listener for VNB -> GEMS communication
 * 
 * @author K. Foerderer
 *
 */
public interface ControlCommunicationListener {

	/**
	 * The VNB wants the GEMS to change the amount of stored energy in the battery.
	 * 
	 * @param soc
	 */
	public void setTargetSOC(int soc, long time);
	
	
	/**
	 * The VNB wants the GEMS to change the amount of stored energy in the battery.
	 * 
	 * @param soc
	 */
	public void setTargetWh(int wh, long time);
	
}
