package de.fzi.osh.wamp.schedule;

/**
 * Returns the flexibility with the given id.
 * 
 * @param id
 * @return {@link GetFlexibilityResponse}
 */
public class GetFlexibilityRequest {
	/**
	 * Id of flexibility.
	 */
	public int id;
	
	@Override
	public String toString() {
		return GetFlexibilityRequest.class.getName() + "{id: " + id + "}";
	}
}
