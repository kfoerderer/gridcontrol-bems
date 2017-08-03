package de.fzi.osh.forecasting.demand.implementation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Provides a standard load profile which is loaded from a csv file
 * 
 * @author K. Foerderer
 *
 */
public class StandardLoadProfile {

	private Logger log = Logger.getLogger(StandardLoadProfile.class.getName());

	/**
	 * For data storage of profile junks
	 * 
	 * @author K. Foerderer
	 *
	 */
	private static class PartialProfile {		
		public int numberOfSlots;
		public int slotLength;
		
		public int startDay;
		public int startMonth;
		
		public int endDay;
		public int endMonth;
		
		/**
		 * {Saturday, Sunday, weekday} x {Slot 1, ..., Slot n}
		 */
		public double[][] values;		
	}
	
	private List<PartialProfile> parts;
	
	/**
	 * Loads the profile from a ";" separated csv file
	 * 
	 * @param file
	 */
	public void load(String file) {
		load(file, ";");
	}
	
	/**
	 * Loads a csv file
	 * 
	 * @param file
	 * @param separator
	 */
	public void load(String file, String separator) {
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			parts = new ArrayList<PartialProfile>();
			
			String line;
			while((line = reader.readLine()) != null) {
				String[] data = line.split(separator);
				
				// read header
				PartialProfile profilePart = new PartialProfile();
				
				profilePart.numberOfSlots = Integer.parseInt(data[1]);
				profilePart.slotLength = Integer.parseInt(data[3]);
				
				// we now assume that there is no data missing
				// hence no null checks
				
				// 2nd header line
				line = reader.readLine();
				data = line.split(separator);

				if(data[0].equalsIgnoreCase("from")) {
					profilePart.startDay = Integer.parseInt(data[1].split("\\.")[0]);
					profilePart.startMonth = Integer.parseInt(data[1].split("\\.")[1]);
					
					profilePart.endDay = Integer.parseInt(data[3].split("\\.")[0]);
					profilePart.endMonth = Integer.parseInt(data[3].split("\\.")[1]);
				} else {
					// there is no period given -> the "else" case
					profilePart.startDay = 1;
					profilePart.startMonth = 1;
					profilePart.endDay = 31;
					profilePart.endMonth = 12;
				}
				
				// 3rd line is only labels
				line = reader.readLine();
				
				// now read the actual profile
				profilePart.values = new double[3][profilePart.numberOfSlots];
				
				for(int i = 0; i < profilePart.numberOfSlots; i++) {
					line = reader.readLine();
					data = line.split(separator);
					
					profilePart.values[0][i] = Double.parseDouble(data[1]);
					profilePart.values[1][i] = Double.parseDouble(data[2]);
					profilePart.values[2][i] = Double.parseDouble(data[3]);
				}
				
				parts.add(profilePart);
			}
			
		} catch (Exception e) {
			log.severe(e.getMessage());
		}
	}
	
	/**
	 * Returns the slp wattage for the given time stamp
	 * 
	 * @param time
	 * @return
	 */
	public double getWattageForTimestamp(Timestamp time) {
		
		// get calendar to determine day
		ZonedDateTime zonedTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time.getTime()), ZoneId.systemDefault());

		// figure out which data to use, depending on the date
		int day = zonedTime.getDayOfMonth();
		int month = zonedTime.getMonthValue();		
		PartialProfile profile = null;
		for(PartialProfile part : parts) {
			if(part.endMonth < part.startMonth || (part.endMonth == part.startMonth && part.endDay < part.startDay)) {
				// different years
				if( (part.startMonth < month || part.startMonth == month && part.startDay <= day) || (month < part.endMonth || month == part.endMonth && day <= part.endDay) ) {
					profile = part;
					// use the first fitting data
					break;
				}
			} else {
				// same years
				if((part.startMonth < month || part.startMonth == month && part.startDay <= day) && (month < part.endMonth || month == part.endMonth && day <= part.endDay)) {
					profile = part;
					// use the first fitting data
					break;
				}
			}
		}
		
		// determine corresponding value		
		DayOfWeek dayOfWeek = zonedTime.getDayOfWeek();
		if(dayOfWeek == DayOfWeek.SATURDAY) {
			day = 0;
		} else if(dayOfWeek == DayOfWeek.SUNDAY) {
			day = 1;
		} else {
			day = 2;
		}
		
		int hour = zonedTime.getHour();
		int minute = zonedTime.getMinute();
		
		int totalMinutes = hour * 60 + minute;
		return profile.values[day][totalMinutes / profile.slotLength];	
	}
	
}
