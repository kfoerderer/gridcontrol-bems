package de.fzi.osh.data.logging.types;

import de.fzi.osh.data.storage.timeseries.StorableTimeSeriesObservation;
import de.fzi.osh.data.storage.timeseries.TimeSeries;

/**
 * Data object for storing 'enable-signal' data. Only changes are recorded.
 * 
 * @author K. Foerderer
 *
 */

@TimeSeries(name="GcuEnableData")
public class GcuEnableData extends StorableTimeSeriesObservation{
	
	/**
	 * Whether it was an enable or disable.
	 */
	@TimeSeries.Series
	public boolean enabled;
}
