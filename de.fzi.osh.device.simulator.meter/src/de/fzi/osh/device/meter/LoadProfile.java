package de.fzi.osh.device.meter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Provides data read from a load profile.
 * 
 * @author K. Foerderer
 *
 */
public class LoadProfile {
	
	private NavigableMap<Integer, Double> profile;
	
	public LoadProfile() {
		profile = new TreeMap<Integer, Double>();
	}
	
	public void put(int secondOfDay, double power) {
		profile.put(secondOfDay, power);
	}
	
	public double getPower(Instant time) {
		ZonedDateTime dateTime = time.atZone(ZoneId.systemDefault());
		int secondOfDay = (int)(time.getEpochSecond() - dateTime.truncatedTo(ChronoUnit.DAYS).toEpochSecond());
		
		Integer key = profile.floorKey(secondOfDay);
		if(null != key) {
			return profile.get(key);
		}
		return 0;
	}
}
