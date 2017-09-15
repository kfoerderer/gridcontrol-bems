package de.fzi.osh.device.battery.data;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import de.fzi.osh.core.oc.DataObject;
import de.fzi.osh.types.flexibilities.Flexibility;
import de.fzi.osh.types.flexibilities.Task;

/**
 * Data structure for schedule data. This is used for data storage to achieve persistence.
 * 
 * @author K. Foerderer
 *
 */
public class BatterySchedulerData implements DataObject {
	/**
	 * Constructor
	 */
	public BatterySchedulerData() {
		// initialization
		tasks = new HashMap<Integer, Task>();
		scheduledPower = new TreeMap<Long, Integer>();
		scheduledPower.put((long)0, 0);		
		flexibilities = new HashMap<Integer, Flexibility>();
	}
	/**
	 * For id creation
	 */
	public int idCounter = 0;
	/**
	 * For reaching target soc until given time
	 */
	public int targetSOC = -1;
	/**
	 * For reaching target soc until given time
	 */
	public long targetSocTime = 0;
	/**
	 * Map holding all currently published flexibilities: id -> flexibility
	 */
	public Map<Integer, Flexibility> flexibilities;
	
	/**
	 * Mapping id -> task data
	 */
	public Map<Integer, Task> tasks;
	
	/**
	 * Mapping epoch second count -> power
	 */
	public NavigableMap<Long, Integer> scheduledPower;	
}
