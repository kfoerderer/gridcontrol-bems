package de.fzi.osh.types.flexibilities;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import de.fzi.osh.types.math.IntInterval;
import de.fzi.osh.types.math.LongInterval;

/**
 * Represents a flexibility
 * 
 * @author K. Foerderer
 *
 */
public class Flexibility {	
	
	/**
	 * Constructor
	 */
	public Flexibility() {
		stoppingTime = new LongInterval();
		runningTime = new IntInterval();
		powerCorridor = new TreeMap<Integer, IntInterval>();
		energyCorridor = new TreeMap<Integer, IntInterval>();
	}
	
	/**
	 * RegisterRequest for constraints. Flexibilities & tasks must have a persistent and unique identifier until they expire. 
	 * The id may then be reused. 
	 */
	public int id;
	
	/**
	 * The id of the scheduled task (>=0).
	 */
	public int taskId;
	
	/**
	 * Whether this is an adaptable flexibility or not.
	 */
	public boolean adaptable;
	
	/**
	 * Stopping time for a task created from this flexibility.
	 */
	public LongInterval stoppingTime;
	
	/**
	 * Running time interval in seconds.
	 */
	public IntInterval runningTime;
	
	/**
	 * Provides a corridor of feasible power consumptions in W depending on time.
	 * i -> ii
	 */
	public NavigableMap<Integer, IntInterval> powerCorridor;
	
	/**
	 * Provides a corridor of feasible energy consumptions in Ws depending on time.
	 * i -> ii
	 */
	public NavigableMap<Integer, IntInterval> energyCorridor;
	
	
	/**
	 * Checks whether a given power path is valid or not
	 * 
	 * @param startingTime
	 * @param power
	 * @return
	 */
	public boolean checkValidity(long startingTime, NavigableMap<Integer, Integer> power) {
		
		// entry at t=0 is mandatory
		if(power.get(0) == null) {
			return false;
		}
		
		// last key has to be equal to zero and can't exceed the maximum running time
		Map.Entry<Integer, Integer> lastEntry = power.lastEntry();
		if(lastEntry.getValue() != 0 || lastEntry.getKey() > runningTime.max) {
			return false;
		}	
		
		// check stopping time
		if(startingTime + lastEntry.getKey() < this.stoppingTime.min || this.stoppingTime.max < startingTime + lastEntry.getKey()) {
			return false;
		}
		
		/* check power */
		// check each point in time in powerCorridor.keySet and powers.keySet
		int time = 0;
		while(true) {
			Integer nextPower = power.higherKey(time);
			Integer nextPowerConstraint = powerCorridor.higherKey(time);

			if(nextPower == null && nextPowerConstraint == null) {
				break; // end reached
			} else if(nextPower == null) {
				nextPower = nextPowerConstraint; // from now on power = 0 => energy remains constant
			} else if(nextPowerConstraint == null) {
				nextPowerConstraint = nextPower; // from now on power constraint remains constant
			}
			
			int nextTime = Math.min(nextPower, nextPowerConstraint);
			int currentPower = power.floorEntry(time).getValue();
			
			IntInterval constraint = powerCorridor.floorEntry(time).getValue();
			if(currentPower < constraint.min || currentPower > constraint.max) {
				return false;
			}
			
			// do time step
			time = nextTime;
		}
		
		/* check energy */
		// check each point in time in energyCorridor.keySet and powers.keySet
		time = 0;
		int energy = 0;
		while(true) {
			Integer nextPower = power.higherKey(time);
			Integer nextEnergyConstraint = energyCorridor.higherKey(time);
			
			if(nextPower == null && nextEnergyConstraint == null) {
				break; // end reached
			} else if(nextPower == null) {
				nextPower = nextEnergyConstraint; // from now on power = 0 => energy remains constant
			} else if(nextEnergyConstraint == null) {
				nextEnergyConstraint = nextPower; // nextPower exceeds the time of the last energy corridor entry 
			}
			
			int nextTime = Math.min(nextPower, nextEnergyConstraint);
			int currentPower = power.floorEntry(time).getValue();
			
			energy += currentPower * (nextTime - time);		
			IntInterval constraint = getEnergyConstraint(nextTime);
			if(energy < constraint.min || energy > constraint.max) {
				return false;
			}
			
			// do time step
			time = nextTime;
		}
		
		return true;
	}
	
	/**
	 * Returns the energy constraint for any point in time, i.e. interpolates the energy corridor. The corridor is continued after the last entry.
	 * 
	 * @param time
	 * @return EnergyConstraint constraint at given time. <i>null</i> if time < 0. 
	 */
	public IntInterval getEnergyConstraint(int time) {
		IntInterval energy = new IntInterval();
		
		Map.Entry<Integer, IntInterval> floor = energyCorridor.floorEntry(time);
		Map.Entry<Integer, IntInterval> higher = energyCorridor.higherEntry(time);
		
		if(floor == null) {
			// invalid point in time
			return null;
		}
		
		if(higher == null) {
			// end reached, use data of previous point in time
			energy.max = floor.getValue().max;
			energy.min = floor.getValue().min;
			return energy;
		}
		
		int floorTime = floor.getKey();
		int higherTime = higher.getKey();
		
		// interpolate linearly
		double higherFactor = (time - floorTime) / (double)(higherTime - floorTime);
		energy.min = (int)((1 - higherFactor) * floor.getValue().min + higherFactor * higher.getValue().min);
		energy.max = (int)((1 - higherFactor) * floor.getValue().max + higherFactor * higher.getValue().max);
		
		return energy;
	}
	
	/**
	 * Returns the energy constraint for a given time when following the given schedule. 
	 * 
	 * @param power Schedule to follow.
	 * @param time Time of energy constraint.
	 * @return Energy constraint.
	 */
	public IntInterval getEnergyConstraint(NavigableMap<Integer, Integer> power, int time) {
		IntInterval constraint = getEnergyConstraint(time);
		
		//System.out.print("Energy: ");
		// compute energy
		int timeIterator = 0;
		int energy = 0;
		while(true) {			
			int currentPower = power.floorEntry(timeIterator).getValue(); // W
			Integer nextTime = power.higherKey(timeIterator);
			if(nextTime == null || nextTime > time) {
				nextTime = time;
			}
			
			energy += currentPower * (nextTime - timeIterator); // Ws
			//System.out.print("(" + nextTime + ", " + energy + ")");
			
			// do time step
			timeIterator = nextTime;
			
			if(timeIterator >= time) {
				constraint.max -= energy;
				constraint.min -= energy;
				//System.out.println("");
				return constraint;
			}
		}
	}
	
	/**
	 * Generates a flexibility indicating how this task could be modified.
	 * 
	 * [simple implementation that doesn't deal with flexible starting time]
	 * 
	 * @param task
	 * @return
	 */
	public Flexibility determineTaskFlexibility(Task task) {
		Flexibility result = new Flexibility();
		// not all parameters need to be set
		
		// TODO: deal with changing starting time
		result.stoppingTime.min = task.startingTime + task.runningTime;
		result.stoppingTime.max = task.startingTime + task.runningTime;
		result.runningTime.min = task.runningTime;
		result.runningTime.max = task.runningTime;
		
		result.powerCorridor = new TreeMap<Integer, IntInterval>();
		result.energyCorridor = new TreeMap<Integer, IntInterval>();
		
		NavigableMap<Integer, Integer> power = new TreeMap<Integer, Integer>(task.power);
		
		int time = 0;
		int energy = 0;
		while(true) {
			Integer nextPower = power.higherKey(time);
			Integer nextEnergyConstraint = energyCorridor.higherKey(time);
			Integer nextPowerConstraint = powerCorridor.higherKey(time);
			
			if(nextPower == null && nextEnergyConstraint == null && nextPowerConstraint == null) {
				break; // end reached
			} else { 
				if(nextPower == null) {
					nextPower = nextEnergyConstraint == null ? nextPowerConstraint : nextEnergyConstraint; // from now on power = 0 => energy remains constant
				}
				if(nextEnergyConstraint == null) {
					nextEnergyConstraint = nextPower; // nextPower exceeds the time of the last energy corridor entry 
				}
				if(nextPowerConstraint == null) {
					nextPowerConstraint = nextPower;
				}
			}
			
			int nextTime = Math.min(nextPowerConstraint, Math.min(nextPower, nextEnergyConstraint));
			
			IntInterval powerConstraint = powerCorridor.floorEntry(time).getValue();
			IntInterval energyConstraint = getEnergyConstraint(time);
			Map.Entry<Integer, Integer> floorEntry = power.floorEntry(time);
			int currentPower = (floorEntry == null ? 0 : floorEntry.getValue());
			energy += currentPower * (nextTime - time);		
			
			// determine flexibility
			result.powerCorridor.put(time, new IntInterval(powerConstraint.min - currentPower, powerConstraint.max - currentPower));
			result.energyCorridor.put(time, new IntInterval(energyConstraint.min - energy, energyConstraint.max - energy));
			
			// do time step
			time = nextTime;
		}
		
		return result;
	}
}