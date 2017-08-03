package de.fzi.osh.wamp.schedule;

/**
 * Parameters for requesting a task:
 * Returns the scheduled task with the given id.
 * 
 * @param id
 * @return {@link GetTaskResponse}
 */
public class GetTaskRequest {
	
	/**
	 * Id of Task to return.
	 */
	public int id;
	
	@Override
	public String toString() {
		return GetTaskRequest.class.getName() + "{id: " + id + "}";
	}
}
