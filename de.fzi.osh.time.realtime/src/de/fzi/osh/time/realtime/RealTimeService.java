package de.fzi.osh.time.realtime;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import de.fzi.osh.time.TimeService;

@Component(immediate=true, service=TimeService.class)
public class RealTimeService implements TimeService{
	
	private static Logger log = Logger.getLogger(RealTimeService.class.getName());

	@Activate
	protected synchronized void activate() throws Exception {
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
	}
	
	@Override
	public long now() {
		return Instant.now().getEpochSecond();
	}

	@Override
	public ZonedDateTime nowAsZonedDateTime() {
		return ZonedDateTime.now();
	}

	@Override
	public Instant nowAsInstant() {
		return Instant.now();
	}

	@Override
	public void sleep(int millis) throws InterruptedException {
		Thread.sleep(millis);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable task, long delay) {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		return executor.schedule(task, delay, TimeUnit.MILLISECONDS);
	}

	@Override
	public ScheduledFuture<?> scheduleOnTime(Runnable task, long time) {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		return executor.schedule(task, time - Instant.now().getEpochSecond(), TimeUnit.SECONDS);
	}

	@Override
	public ScheduledFuture<?> scheduleAtDelay(Runnable task, long initialDelay, long delay) {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		return executor.scheduleWithFixedDelay(task, initialDelay, delay, TimeUnit.MILLISECONDS);
	}

	@Override
	public ScheduledFuture<?> scheduleAtRate(Runnable task, long initialDelay, long rate) {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		return executor.scheduleAtFixedRate(task, initialDelay, rate, TimeUnit.MILLISECONDS);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable task, Integer second, Integer minute, Integer hour) {
		long period = (second == null ? 1 : 60);
		ZonedDateTime now = ZonedDateTime.now();
		// initialDelay = time till next second + (if relevant) time till next minute [ = 1000 * (60 - 1 - second)]
		long initialDelay = (1000 - now.getNano() / 1000000) + 1000 * (second == null ? 0 : 59 - now.getSecond()) + 1;
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		return executor.scheduleAtFixedRate(new Runnable() {			
			private long nextExecution = computeNextExecutionTime(ZonedDateTime.now());			
			
			@Override
			public void run() {
				try {
					ZonedDateTime now = ZonedDateTime.now();
					if(nextExecution <= now.toEpochSecond()) {
						log.fine("Running chron task.");
						task.run();
						nextExecution = computeNextExecutionTime(now);
					}	
				} catch(Exception e) {
						log.severe(e.toString());
				}
			}
			
			private long computeNextExecutionTime(ZonedDateTime now) {
				ZonedDateTime next = now;
				
				if(null == second) {
					// second doesn't matter , just use next second
					next = next.plusSeconds(1);
					
					if(null == minute) {
							// null,null,null => every second
						if(null != hour) {
							// null, null, hour => every second of hour 
							next = next.withHour(hour);
						}
					} else {
						next = next.withMinute(minute);
						if(null == hour) {
							// null, minute, null => every second of minute
							if(!next.isAfter(now)) { // is the next second in another minute?
								next = next.plusHours(1); // => next hour
							}
						} else {
							// null, minute, hour
							next = next.withHour(hour);
						}
					}
				} else {
					// a fixed second
					next = next.withSecond(second);
					
					if(null == minute) {
						next = next.plusMinutes(1);
							// second, null, null => next minute again 
						if(null != hour) {
							// second, null, hour => next minute when hour matches
							next = next.withHour(hour);
						}
					} else {
						// fixed minute
						next = next.withMinute(minute);
						if(null == hour) {
							// second, minute, null => next hour again
							next = next.plusHours(1);
						} else {
							// second, minute, hour => next day again
							next = next.withHour(hour);
						}
					}
				}
				
				// if $next is not after $now (due to setting values), the next execution is 1 day later
				if(!next.isAfter(now)) {
					next = next.plusDays(1);
				}
				
				// DEBUG
				log.fine("Chron schedule, next execution on " + next);
				return next.toEpochSecond();
			}
		}, initialDelay, period * 1000, TimeUnit.MILLISECONDS);
	}

	@Override
	public long convert(long amount) {
		return amount;
	}
}
