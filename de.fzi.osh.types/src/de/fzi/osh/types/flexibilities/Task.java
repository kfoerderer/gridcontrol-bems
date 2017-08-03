package de.fzi.osh.types.flexibilities;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Class representing a task. If a flexibility results in a task, the task id must equal the flexibilities id
 * 
 * @author K. Foerderer
 *
 */
public class Task {	
	/**
	 * RegisterRequest. Flexibilities & tasks must have a persistent and unique identifier until they expire. 
	 * The id may then be reused. 
	 */
	public int id;
	
	/**
	 * The id of the flexibility this task has been generated from (>=0).
	 */
	public int flexibilityId;	
	
	/**
	 * Whether this is the task for an adaptable flexibility or not. An adaptable task can only be created from a flexibility. 
	 */
	public boolean adaptable;
	
	/**
	 * Starting time as epoch seconds. After this point in time has been passed only cancellable tasks can be canceled.
	 */
	public long startingTime;
	
	/**
	 * Running time, must match the last entry of power
	 */
	public int runningTime;
	
	/**
	 * PowerConstraint consumption as mapping seconds -> w
	 */
	public NavigableMap<Integer, Integer> power;
	
	/**
	 * Compresses a tasks power mapping by deleting redundant values.
	 * 
	 * @return Number of deleted entries.
	 */
	public int compress() {
		Iterator<Map.Entry<Integer, Integer>> iterator = power.entrySet().iterator();
		int removed = 0;
		int previousPower = 0;
		// iterator over entries
		while(iterator.hasNext()) {
			Map.Entry<Integer, Integer> entry = iterator.next();
			// first and last entry are fixed
			if(entry.getKey() != 0 && entry.getKey() != runningTime) {
				if(entry.getValue() == previousPower) {
					// power value equals the previous one
					
					// CAUTION: iterator.remove changes $entry to the next one
					previousPower = entry.getValue();
					// remove this entry
					iterator.remove();
					removed++;
				} else {
					previousPower = entry.getValue();
				}
			}
			
		}
		return removed;
	}
} 