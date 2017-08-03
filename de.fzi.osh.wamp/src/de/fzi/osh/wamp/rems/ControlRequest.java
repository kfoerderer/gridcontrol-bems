package de.fzi.osh.wamp.rems;

import java.util.Map;

/**
 * Route data for Rems. Only write-able addresses.
 * 
 * @author Foerderer K.
 *
 */
public class ControlRequest {
	/**
	 * Set coils.
	 */
	public Map<Short, Boolean> coils;
	/**
	 * Set registers.
	 */
	public Map<Short, Short> registers;
}
