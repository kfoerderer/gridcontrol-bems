package de.fzi.osh.data.logging.types;

import java.util.UUID;


import de.fzi.osh.data.storage.timeseries.TimeSeries;
import de.fzi.osh.data.storage.timeseries.StorableTimeSeriesObservation;


@TimeSeries(name="MeterData")
public class MeterData extends StorableTimeSeriesObservation {

	/**
	 * UUID of source.
	 */
	public UUID uuid;
		
	/**
	 * Total active power in W / 10.
	 */
	@TimeSeries.Series
	public int totalActivePower;
	/**
	 * Total reactive power in VAr / 10.
	 */
	@TimeSeries.Series
	public int totalReactivePower;
	/**
	 * Total active energy (+) in Wh / 100.
	 */
	@TimeSeries.Series
	public long totalActiveEnergyP;
	/**
	 * Total active energy (-) in Wh / 100;
	 */
	@TimeSeries.Series
	public long totalActiveEnergyN;
	/**
	 * Alarm flag (see gcu documentation).
	 */
	@TimeSeries.Tag
	public long alarmFlag;
}
