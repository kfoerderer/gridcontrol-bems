package de.fzi.osh.wamp.device;

/**
 * Driver state enumeration.
 * 
 * <b>Caution: Actual device state doesn't necessarily correspond to the driver state!</b>
 * 
 * @author K. Foerderer
 *
 */
public enum DriverState {
	Off, Standby, On, Unknown;
}