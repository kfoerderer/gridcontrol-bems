package de.fzi.osh.scheduling.configuration;

import java.util.UUID;

import de.fzi.osh.core.component.OshComponentConfiguration;

public class SchedulerConfiguration extends OshComponentConfiguration{
	/**
	 * Time to wait in ms before initializing.
	 */
	public int startupDelay = 10000; // = 10s
	/**
	 * Persistence file.
	 */
	public String schedulerDataFile = "persistence_scheduler.json";
	/**
	 * On schedule deviation the flexibility is adapted within this time horizon [seconds] starting from now. 
	 */
	public int flexibilityAdaptionHorizon = 80;
	/**
	 * The schedule is adapted every # milliseconds.
	 */
	public long scheduleAdpationInterval = 60*1000; // = 60s, GCU only provides Wh -> +1Wh = 60W 
	/**
	 * Minimum time in seconds between two target schedule optimization runs caused by flexibility adaption.
	 */
	public int minimumComplianceOptimizationInterval = 15*60; // = 15 min
	/**
	 * Minimum time in seconds between two schedule update publications.
	 */
	public int minimumScheduleUpdatePublicationInterval = 5 * 60;
	/**
	 * Threshold for reporting schedule deviations in Wh.
	 */
	public int scheduleDeviationReportingThreshold = 250;
	/**
	 * Time slot length for (target-) schedule optimization in seconds.
	 */
	public int scheduleOptimizationSlotLength = 15 * 60;
	/**
	 * Buffer for adaptations in Ws.
	 */
	public int flexibilityAdaptationBuffer = 100 * 60 * 60;
	/**
	 * Time buffer in seconds for computation time. Flexibilities that have to be scheduled within this time buffer can't be included, since optimization takes too long.
	 */
	public int optimizationTimeBuffer = 1;
	/**
	 * Time waited in seconds until another publishing attempt is started.
	 */
	public int fmsFailureWaitingTime = 5 * 60; // = 5 min
	/**
	 * UUIDs of devices that provide flexibilities.
	 */
	public UUID[] flexibilityProviders;
	/**
	 * UUIDs of devices that listen for the enabled signal.
	 */
	public UUID[] dnoControllableDevices;
	/**
	 * Simplified cron expression for schedule publishing task. Format: "s m h" with integers s,m,h for second, minute and hour or '*' for any value.
	 */
	public String fmsSchedulePublishingCronExpression = "0 0 9";
	/**
	 * Interval for updating data that is provided to and from (=enabled) the REMS in ms.
	 */
	public long remsDataProvisionInterval = 10000;
}
