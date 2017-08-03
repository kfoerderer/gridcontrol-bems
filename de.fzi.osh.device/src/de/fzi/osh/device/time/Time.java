package de.fzi.osh.device.time;

import de.fzi.osh.time.TimeService;

/**
 * Provides a time service for timing tasks.
 * 
 * @author K. Foerderer
 *
 */
public class Time{
	private static TimeService service;
	
	public static void initialize(TimeService service) {
		Time.service = service;
	}
	
	public static TimeService service() {
		return service;
	}
}
