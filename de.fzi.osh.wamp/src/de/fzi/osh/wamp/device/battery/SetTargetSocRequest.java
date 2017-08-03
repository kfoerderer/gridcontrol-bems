package de.fzi.osh.wamp.device.battery;

/**
 * Request for setting target soc.
 * 
 * @author K. Foerderer
 *
 */
public class SetTargetSocRequest {
	/**
	 * Target soc.
	 */
	public int soc;
	/**
	 * Point in time to reach target soc.
	 */
	public long time;
}
