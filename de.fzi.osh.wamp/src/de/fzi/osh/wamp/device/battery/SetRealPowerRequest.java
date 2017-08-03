package de.fzi.osh.wamp.device.battery;

/**
 * Request for setting real power.
 * 
 * @author K. Foerderer
 *
 */
public class SetRealPowerRequest {
	/**
	 * Real power. + charge, - discharge
	 */
	public int realPower;
}
