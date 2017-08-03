package de.fzi.osh.core.timeseries;

import java.util.NavigableMap;
import java.util.Map.Entry;
import java.util.logging.Logger;

/**
 * Transforms time series data in a slot based time series by using averages. The resulting time series has one value for each time slot. A value marks the begin of a slot.
 * 
 * @author K. Foerderer
 *
 */
public class IntegerTimeSlotTransformer implements Transformer<Integer>{

	private Logger log = Logger.getLogger(IntegerTimeSlotTransformer.class.getName());
	
	/**
	 * Start of first slot 
	 */
	private long start;
	/**
	 * Number of slots to fill beginning from start
	 */
	private long end;
	/**
	 * Length of a slot
	 */
	private int slotLength;
	
	/**
	 * Constructor
	 * 
	 * 
	 * @param start start of first slots
	 * @param end time last time slot has ended (= start + nr slots * slotLength)
	 * @param slotLength length of a slot in the given series' ChronoUnit
	 */
	public IntegerTimeSlotTransformer(long start, long end, int slotLength) {
		this.start = start;
		this.end = end;
		this.slotLength = slotLength;
		
		if(slotLength <= 0) {
			log.severe("Slot length must be positive");
			throw new IllegalArgumentException("Slot length must be positive");
		}
	}
	
	@Override
	public TimeSeries<Integer> transform(TimeSeries<Integer> source) {
		
		TimeSeries<Integer> series = new TimeSeries<Integer>(source.getChronoUnit(), source.getInterpolator());
		
		NavigableMap<Long, Integer> values = source.getValues();
		
		long previousSlotTime = start;
		long previousTime = start;
		int previousValue = 0;
		int sum = 0;
		Long floorKey = values.floorKey(start);
		if(null == floorKey) {
			log.warning("Start of target series is before start of source series. Assuming 0.");
		} else {
			previousValue = values.get(floorKey);
		}
		
		for(Entry<Long, Integer> entry : values.entrySet()) {
			long time = entry.getKey();
			
			// if end of time slot has been reached/exceeded, setup a new slot.
			// if some slots have been omitted, fill them with data (assuming a constant value).
			while(time - previousSlotTime >= slotLength) {
				// remaining length of slot
				int length = (int)(previousSlotTime + slotLength - previousTime);
				sum += previousValue * length;
				series.add(previousSlotTime, sum / slotLength);
				// update tracking variables
				previousSlotTime += slotLength;
				previousTime = previousSlotTime;
				sum = 0;
				
				if(previousSlotTime >= end) {
					// given size has been reached => return result
					return series;
				}
			}
			// the previous value could still carry over into the current slot

			// add to sum weighed with time.
			int length = (int)(time - previousTime);
			sum += previousValue * length;
			// update tracking variables
			previousValue = entry.getValue();
			previousTime = time;
		}
		
		// add last slot, assuming the last value remains true
		
		// remaining length of slot
		int length = (int)(previousSlotTime + slotLength - previousTime);
		sum += previousValue * length;
		series.add(previousSlotTime, sum / slotLength);
		
		// return result
		return series;
	}

}
