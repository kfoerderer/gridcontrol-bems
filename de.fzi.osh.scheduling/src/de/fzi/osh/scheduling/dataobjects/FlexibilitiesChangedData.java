package de.fzi.osh.scheduling.dataobjects;

import java.util.UUID;

import de.fzi.osh.core.oc.DataObject;

/**
 * Data object that encapsules a flexibilities changed signal
 * 
 * @author K. Foerderer
 *
 */
public class FlexibilitiesChangedData implements DataObject{
	/**
	 * The source UUID of this event.
	 */
	public UUID source;
	/**
	 * Timestamp of first change in flexibilitites.
	 */
	public long from;
}
