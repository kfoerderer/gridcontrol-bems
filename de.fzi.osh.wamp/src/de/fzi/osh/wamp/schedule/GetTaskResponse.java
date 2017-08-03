package de.fzi.osh.wamp.schedule;

import de.fzi.osh.types.flexibilities.Task;

/**
 * Response for a task request.
 * 
 * @author K. Foerderer
 *
 */
public class GetTaskResponse {
	/**
	 * Task that has been asked for.
	 */
	public Task task;
}
