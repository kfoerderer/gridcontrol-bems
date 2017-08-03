package de.fzi.osh.wamp.schedule;

/**
 * Parameters for a schedule request:
 * Returns all flexibilities within the given time period between $from (>=now) and $to.
 * 
 * @param from Starting time as Unix time.
 * @param to End time as Unix time.
 * @return {@link GetScheduleResponse}
 */
public class GetScheduleRequest {
	/**
	 * Lower time frame border.
	 */
	public long from;
	/**
	 * Upper time frame border.
	 */
	public long to;
	
	@Override
	public String toString() {
		return GetScheduleRequest.class.getName() + "{from: " + from + ", to: " + to + "}";
	}
}
