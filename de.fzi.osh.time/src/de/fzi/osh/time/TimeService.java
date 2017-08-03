package de.fzi.osh.time;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;

/**
 * Service providing current time and other timing related functions. This is needed for simulation purposes.
 * 
 * @author K. Foerderer
 *
 */
public interface TimeService {
	/**
	 * Returns the "current" epoch second
	 * 
	 * @return
	 */
	public long now();
	
	/**
	 * Returns the "current" time as ZonedDateTime
	 * 
	 * @return
	 */
	public ZonedDateTime nowAsZonedDateTime();
	
	/**
	 * Returns the "current" time as instant
	 * 
	 * @return
	 */
	public Instant nowAsInstant();
	
	/**
	 * Sends the running thread to sleep for (simulated) $millis ms.
	 */
	public void sleep(int millis) throws InterruptedException;
	
	/**
	 * Schedules a task to run once after the given delay.
	 * 
	 * @param task
	 * @param delay in ms
	 */
	public ScheduledFuture<?> schedule(Runnable task, long delay);

	/**
	 * Schedules a task to be run at a given epoch second.
	 * 
	 * @param task
	 * @param time
	 * @return 
	 */
	public ScheduledFuture<?> scheduleOnTime(Runnable task, long time);
	
	/**
	 * Schedules a task for repeated execution with a fixed delay applied after each execution. 
	 * 
	 * @param task
	 * @param initialDelay in ms
	 * @param delay in ms
	 */
	public ScheduledFuture<?> scheduleAtDelay(Runnable task, long initialDelay, long delay);
	
	/**
	 * Schedules a task for repeated execution at a fixed rate. 
	 * Subsequent executions are postponed if the execution takes longer than the given rate.
	 * 
	 * Rate:
	 * 	n		n+1		n+2		n+3
	 * 	|		| 		|		| 
	 * 
	 * 	1___	2___	3___			rate > running time
	 * 
	 * 	1_________2___	3___			execution 2 is delayed
	 * 
	 * 	1_____________________2___3____	execution 2 & 3 is delayed
	 * 
	 * @param task
	 * @param initialDelay in ms
	 * @param rate in ms
	 */
	public ScheduledFuture<?> scheduleAtRate(Runnable task, long initialDelay, long rate);

	/**
	 * Schedules a task to be run on every point in time matching the given parameters. 
	 * Value <b>null</b> equals any value for this parameter. (* in chron)
	 * 
	 * @param second
	 * @param minute
	 * @param hour
	 */
	public ScheduledFuture<?> schedule(Runnable task, Integer second, Integer minute, Integer hour);
	
	/**
	 * Converts a given amount of time into simulation time.
	 * 
	 * @param amount
	 * @return
	 */
	public long convert(long amount);
}
