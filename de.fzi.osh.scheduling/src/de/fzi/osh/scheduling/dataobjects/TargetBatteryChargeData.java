package de.fzi.osh.scheduling.dataobjects;

import de.fzi.osh.core.oc.DataObject;

/**
 * Data object for vnb -> gems battery charge control
 * 
 * @author K. Foerderer
 *
 */
public class TargetBatteryChargeData implements DataObject{
	/**
	 * Target SOC in %
	 * 
	 * either soc or wh is >= 0
	 */
	public int soc = -1;
	/**
	 * Target charge in Wh
	 * 
	 * either soc or wh is >= 0
	 */
	public int wh = -1;
	/**
	 * Epoch second the target charge should be reached. Up until this point in time, the scheduler does not have to perform any other tasks.
	 */
	public long time;
}
