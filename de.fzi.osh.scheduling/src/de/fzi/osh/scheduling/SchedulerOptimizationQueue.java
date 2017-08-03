package de.fzi.osh.scheduling;

import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;

import de.fzi.osh.scheduling.configuration.SchedulerConfiguration;
import de.fzi.osh.scheduling.dataobjects.SchedulePublishingData;
import de.fzi.osh.scheduling.dataobjects.TargetScheduleOptimizationData;
import de.fzi.osh.time.TimeService;

public class SchedulerOptimizationQueue {

	private Logger log = Logger.getLogger(SchedulerOptimizationQueue.class.getName());
	
	private Object queueLock = new Object();
	private ConcurrentNavigableMap<Long, TargetScheduleOptimizationData> targetOptimizationQueue;
	private ConcurrentNavigableMap<Long, SchedulePublishingData> initialPublicationQueue;
	private ConcurrentNavigableMap<Long, SchedulePublishingData> updatePublicationQueue;
	
	@SuppressWarnings("unused")
	private volatile  long latestInitialPublicationRun;
	private volatile  long latestUpdatePublicationRun = 0;
	private volatile  long latestTargetOptimizationRun = 0;
	
	/**
	 * Worker thread. Waits for a new job in the queue and executes it.
	 * 
	 * @author K. Foerderer
	 *
	 */
	private class Worker  implements Runnable {
		@Override
		public void run() {
	
			long min;
			Long targetOptKey;
			Long initialOptKey;
			Long updateOptKey;
			
			while(true) {
				try {
					long now = timeService.now();
					
					
					// determine which task is the next one to be executed
					synchronized (queueLock) {
						targetOptKey = targetOptimizationQueue.floorKey(now);
						initialOptKey = initialPublicationQueue.floorKey(now);
						updateOptKey = updatePublicationQueue.floorKey(now);
						
						if(targetOptKey == null) {
							targetOptKey = Long.MAX_VALUE;
						}
						if(initialOptKey == null) {
							initialOptKey = Long.MAX_VALUE;
						}
						if(updateOptKey == null) {
							updateOptKey = Long.MAX_VALUE;
						}
						min = Math.min(targetOptKey, Math.min(initialOptKey, updateOptKey));
						
						if(min < Long.MAX_VALUE && min > now) {
							// the next task is in the future
							try {
								queueLock.wait(timeService.convert(min - now) * 1000);
							} catch (InterruptedException e) {
								log.severe(e.toString());
							}
						} else if(min == Long.MAX_VALUE){
							try {
								queueLock.wait();
							} catch (InterruptedException e) {
								log.severe(e.toString());
							}
						}
					}
					
					// time has come
					if(initialOptKey <= now && initialOptKey == min) {
						
						log.info("Opt.Queue: Starting initial publication.");
						SchedulePublishingData data = initialPublicationQueue.get(min);
						synchronized (initialPublicationQueue) {
							latestInitialPublicationRun = now;
							initialPublicationQueue.remove(min);	
						}
						component.getObserver().update(data);
						log.info("Opt.Queue: Finished initial publication.");
						
					} else if(updateOptKey <= now && updateOptKey == min) {
						
						log.info("Opt.Queue: Starting update publication.");
						SchedulePublishingData data = updatePublicationQueue.get(min);
						synchronized (updatePublicationQueue) {
							latestUpdatePublicationRun = now;
							updatePublicationQueue.remove(min);	
						}
						component.getObserver().update(data);
						log.info("Opt.Queue: Finished update publication.");
						
					} else if(targetOptKey <= now && targetOptKey == min) {
						
						log.info("Opt.Queue: Starting target optimization.");
						TargetScheduleOptimizationData data = targetOptimizationQueue.get(min);
						synchronized (targetOptimizationQueue) {
							latestTargetOptimizationRun = now;
							targetOptimizationQueue.remove(min);	
						}
						component.getObserver().update(data);
						log.info("Opt.Queue: Finished target optimization.");
						
					}
				} catch(Exception e) {
					log.severe(e.toString());
					e.printStackTrace();
				}				
			}
		}
	}
	
	private TimeService timeService;
	
	private Scheduler component;
	private SchedulerConfiguration configuration;
	
	public SchedulerOptimizationQueue(Scheduler scheduler) {
		component = scheduler;
		timeService = Scheduler.getTimeService();
		configuration = component.getConfiguration();	
		
		// create queue
		targetOptimizationQueue = new ConcurrentSkipListMap<Long, TargetScheduleOptimizationData>();
		initialPublicationQueue = new ConcurrentSkipListMap<Long, SchedulePublishingData>();
		updatePublicationQueue = new ConcurrentSkipListMap<Long, SchedulePublishingData>();
		
		// setup worker
		Thread workerThread = new Thread(new Worker());
		workerThread.start();
	}
	
	/**
	 * Queues a target schedule optimization. It is only added to the queue if there isn't another one waiting.
	 * 
	 * @param data
	 * @param force if <b>true</b> the optimization is guaranteed to be scheduled
	 */
	public void queueTargetScheduleOptimization(TargetScheduleOptimizationData data, boolean force) {
		synchronized(queueLock) {
			synchronized (targetOptimizationQueue) {
				long now = timeService.now();
				
				Long floor = targetOptimizationQueue.floorKey(now);
				if(null == floor) {
					floor = latestTargetOptimizationRun;
				} else {
					floor = Math.max(floor, latestTargetOptimizationRun);
				}
				Long ceil = targetOptimizationQueue.ceilingKey(now);
				// considering floor key is necessary to prevent multiple scheduling when queuing is done in quick succession 
				if(true == force || (null == ceil && floor + configuration.minimumComplianceOptimizationInterval <= now)) {
					// no future optimization is queued
					log.finest("ceil: '" + ceil + "', floor: '" + targetOptimizationQueue.floorKey(now) + "', min: '" + (floor + configuration.minimumComplianceOptimizationInterval) + "'," );
					log.finest("Queuing target schedule optimization at '" + Math.max(now, floor + configuration.minimumComplianceOptimizationInterval) + "'.");
					
					// schedule this one as early as possible
					targetOptimizationQueue.put(Math.max(now, floor + configuration.minimumComplianceOptimizationInterval), data);
					queueLock.notifyAll();
				} else {
					// there is another optimization waiting => do nothing
				}	
			}
		}
	}
	
	/**
	 * 
	 * @param data
	 */
	public void queueSchedulePublication(SchedulePublishingData data) {
		long now = timeService.now();
		
		log.finest("Queuing schedule publication.");
		
		synchronized(queueLock) {
			log.finest("Queuing schedule publication. Passed queue lock. [initial='" + data.initial + "']");
			if(data.initial == true) {
				// just schedule it
				synchronized (initialPublicationQueue) {
					initialPublicationQueue.put(now, data);
				}
				queueLock.notifyAll();
			} else {				
				
				synchronized (updatePublicationQueue) {
					// updates can only happen for the current day, since there are no triggers for updates of the next day

					Long floor = updatePublicationQueue.floorKey(now);
					if(null == floor) {
						floor = latestUpdatePublicationRun;
					} else {
						floor = Math.max(floor, latestUpdatePublicationRun);
					}
					Long ceil = updatePublicationQueue.ceilingKey(now);
					// considering floor key is necessary to prevent multiple scheduling when queuing is done in quick succession
					if(null == ceil  && floor + configuration.minimumScheduleUpdatePublicationInterval <= now) {
						// no future optimization is queued
						// schedule this one as early as possible
						updatePublicationQueue.put(Math.max(now, floor + configuration.minimumScheduleUpdatePublicationInterval), data);
						queueLock.notifyAll();
					} else {
						// there is another optimization waiting => do nothing
					}	
				}
			}
		}
	}
}
