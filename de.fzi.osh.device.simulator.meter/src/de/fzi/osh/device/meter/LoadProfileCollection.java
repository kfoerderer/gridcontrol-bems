package de.fzi.osh.device.meter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Represents a collection of load profiles.
 * 
 * @author K. Foerderer
 *
 */
public class LoadProfileCollection {

	private LoadProfile loadProfiles[];
	
	private NavigableMap<Long, LoadProfile> profileMapping;
	
	public LoadProfileCollection() {
		profileMapping = new TreeMap<Long, LoadProfile>();
	}
	
	/**
	 * Loads a collection of load profiles from a file.
	 * 
	 * @param filename
	 * @throws IOException 
	 */
	public void load(String filename, String separator) throws IOException {
		InputStream input = new FileInputStream(filename);
		
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {		
			// read header
			String line = reader.readLine();
			
			String[] cells = line.split(separator);
			// read scaling factor for power values
			double scalar = Double.parseDouble(cells[1]);
			
			// read file
			loadProfiles = null;
			line = reader.readLine();
			while(null != line) {
				cells = line.split(separator);
				
				if(null == loadProfiles) {
					// first iteration, create array of profiles
					loadProfiles = new LoadProfile[cells.length - 1];
					for(int i = 0; i < loadProfiles.length; i++) {
						loadProfiles[i] = new LoadProfile();
					}
				}
				
				int secondOfDay = Integer.parseInt(cells[0]);
				for(int i = 1; i < cells.length; i++) {
					double power = Double.parseDouble(cells[i]);
					loadProfiles[i-1].put(secondOfDay, power * scalar);
				}
				
				line = reader.readLine();
			}
		}
	}
	
	/**
	 * Returns the load profile for the given day. If random is <b>true</b> profile selection is randomized, 
	 * else the profiles are returned in the same order they are defined (assuming there is no day skipped).
	 * 
	 * @param time
	 * @param random
	 * @return
	 */
	public LoadProfile getProfile(Instant time, boolean random) {
		Long key = profileMapping.floorKey(time.getEpochSecond());
		long beginOfDay = time.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS).toEpochSecond();
		if(null == key || key != beginOfDay) {
			// no profile for this day has been chosen yet
			
			if(true == random) {
				int index = (int)(Math.random() * loadProfiles.length);
				profileMapping.put(beginOfDay, loadProfiles[index]);
				
				return loadProfiles[index];
			} else {
				if(null == key) {
					// no profile yet
					profileMapping.put(beginOfDay, loadProfiles[0]);			
					return loadProfiles[0];
				} else {
					// get current profile and determine subsequent profile
					LoadProfile current = profileMapping.get(key);
					for(int i = 0; i < loadProfiles.length; i++) {
						if(loadProfiles[i] == current) {
							if(i + 1 < loadProfiles.length) {
								profileMapping.put(beginOfDay, loadProfiles[i+1]);			
								return loadProfiles[i+1];
							} else {
								// go back to first profile after the last one
								profileMapping.put(beginOfDay, loadProfiles[0]);			
								return loadProfiles[0];
							}
						}
					}	
				}
			}
		}
		
		return profileMapping.get(key);
	}
	
}
