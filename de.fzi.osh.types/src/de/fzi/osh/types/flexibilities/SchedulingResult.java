package de.fzi.osh.types.flexibilities;

/**
 * Enumeration for flexibility scheduling results
 * 
 * @author K. Foerderer
 *
 */
public enum SchedulingResult {
	/**
	 * Everything is fine.
	 */
	Ok,
	/**
	 * Action is not allowed.
	 */
	Illegal,
	/**
	 * Invalid data has been passed.
	 */
	InvalidData,
	/**
	 * There is a conflict with another scheduled task.
	 */
	Conflict, 
	/**
	 * The flexibility in question is not known.
	 */
	UnknownFlexibility,
	/**
	 * The task in question is not known.
	 */
	UnknownTask,
	/**
	 * Something went wrong.
	 */
	Fail;
}