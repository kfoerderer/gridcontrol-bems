package de.fzi.osh.data.logging.types;

import java.util.UUID;


import de.fzi.osh.data.storage.timeseries.TimeSeries;
import de.fzi.osh.data.storage.timeseries.StorableTimeSeriesObservation;

/**
 * Data object for storing soc data of batteries.
 * 
 * @author K. Foerderer
 *
 */

@TimeSeries(name="BatterySocData")
public class BatterySocData extends StorableTimeSeriesObservation {
	/**
	 * UUID of the battery having this soc.
	 */
	public UUID uuid;	
	
	/**
	 * State of charge in %.
	 */
	@TimeSeries.Series
	public byte soc;
}
