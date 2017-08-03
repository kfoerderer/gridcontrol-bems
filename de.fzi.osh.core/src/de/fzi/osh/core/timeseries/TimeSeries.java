package de.fzi.osh.core.timeseries;

import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A generic time series.
 * 
 * Note: a time series for primitive types would have to use either Generic containers (and hence object types) or arrays (which are harder to manage) 
 * 
 * @author K. Foerderer
 *
 * @param <T>
 */
public class TimeSeries<T> {
	
	private ChronoUnit unit;
	
	private NavigableMap<Long, T> values;
	private Interpolator<T> interpolator;
	
	/**
	 * Constructor
	 * 
	 * @param scale
	 */
	public TimeSeries(ChronoUnit scale) {
		unit = scale;
		this.interpolator = null;
		this.values = new TreeMap<Long, T>();
	}
	
	/**
	 * Constructor
	 * 
	 * @param scale
	 * @param interpolator
	 */
	public TimeSeries(ChronoUnit scale, Interpolator<T> interpolator){
		unit = scale;
		this.interpolator = interpolator;
	}
	
	/**
	 * Set another interpolator.
	 * 
	 * @param interpolator
	 */
	public void setInterpolator(Interpolator<T> interpolator) {
		this.interpolator = interpolator;
	}
	
	/**
	 * Returns the currently used interpolator.
	 * 
	 * @return
	 */
	public Interpolator<T> getInterpolator() {
		return interpolator;
	}
	
	/**
	 * Sets all values at once
	 * 
	 * @param values
	 */
	public void setValues(NavigableMap<Long, T> values) {
		this.values = values;
	}
	
	/**
	 * Returns a map of all values. Mapping time -> value.
	 * 
	 * @return
	 */
	public NavigableMap<Long, T> getValues() {
		return values;
	}
	
	/**
	 * Sets the value for a given time.
	 * 
	 * @param time
	 * @param value
	 */
	public void add(long time, T value) {
		values.put(time, value);
	}		
	
	/**
	 * Removes a value from the time series.
	 * 
	 * @param time
	 */
	public void remove(long time) {
		values.remove(time);
	}
	
	/**
	 * Returns the latest known value for the given time.
	 * 
	 * @param time
	 * @return
	 */
	public T get(long time) {
		Long key = values.floorKey(time);
		if(null == key) {
			return null;
		}
		return values.get(key);
	}
	
	/**
	 * Returns the time series value at the given time.
	 * 
	 * @param time
	 * @return
	 */
	public T getInterpolated(long time) {
		T value = values.get(time);
		if(value == null) {
			Entry<Long, T> start = values.floorEntry(time);
			if(start == null) {
				start = new AbstractMap.SimpleEntry<Long, T>(Long.MIN_VALUE, null);				
			}
			Entry<Long, T> end = values.ceilingEntry(time);
			if(end == null) {
				end = new AbstractMap.SimpleEntry<Long, T>(Long.MAX_VALUE, null);				
			}
			return interpolator.interpolate(start.getKey(), start.getValue(), end.getKey(), end.getValue(), time);
		}
		return value;
	}
	
	
	/**
	 * Returns the time scale of this time series. A step of one is equivalent to one chrono unit.
	 * 
	 * @return
	 */
	public ChronoUnit getChronoUnit() {
		return unit;
	}
	
	/**
	 * Returns the amount of data points in the series.
	 * 
	 * @return
	 */
	public int size() {
		return values.size();
	}
	
	/**
	 * Returns the part of the time series that lies within the given points in time (both inclusive).
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public TimeSeries<T> subSeries(long from, long to) {
		TimeSeries<T> subSeries = new TimeSeries<T>(unit, interpolator);
		subSeries.values = values.subMap(from, true, to, true);
		return subSeries;
	}
}
