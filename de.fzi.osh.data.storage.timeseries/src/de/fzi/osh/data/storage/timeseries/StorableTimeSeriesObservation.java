package de.fzi.osh.data.storage.timeseries;

import java.time.Instant;

import de.fzi.osh.core.oc.DataObject;

/**
 * Base interface for all storable time series.
 * 
 * @author Foerderer K.
 *
 */
public abstract class StorableTimeSeriesObservation implements DataObject {
	/**
	 * Instant of this observation.
	 */
	public Instant time;
}
