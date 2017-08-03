package de.fzi.osh.data.storage;

/**
 * Base class for data to be stored
 * 
 * Ensures that every object has an id as primary key
 * 
 * @author K. Foerderer
 *
 */
public abstract class StorableDataObject implements de.fzi.osh.core.oc.DataObject {
	// Primary key: INT NOT NULL AUTO_INCREMENT
	protected int id = 0;
	
	/**
	 * Returns the object id
	 * 
	 * @return
	 */
	public int getId() {
		return id;
	}
	
	/**
	 * Set the object id
	 * 
	 * @param id
	 */
	public void setId(int id) {
		this.id = id;
	}
}
