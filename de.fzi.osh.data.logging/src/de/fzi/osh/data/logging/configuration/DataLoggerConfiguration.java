package de.fzi.osh.data.logging.configuration;

import de.fzi.osh.core.component.OshComponentConfiguration;

public class DataLoggerConfiguration extends OshComponentConfiguration {
	/**
	 * Time in seconds until cached data is deleted.
	 */
	public int cachingDuration = 24 * 60 * 60; // 24h
}
