package de.fzi.osh.com.fms;

/**
 * Listener for FMS -> GEMS communication
 * 
 * @author K. Foerderer
 *
 */
public interface FmsCommunicationListener {

	/**
	 * The FMS wants the GEMS to follow a new schedule [mandatory].
	 * 
	 * [!] This callback function has to make sure the schedule isn't lost in case of a restart.
	 * 
	 * The schedule on the server is only then marked as read when this method executes without throwing an exception.
	 * 
	 * @param schedule
	 */
	public void updateSchedule(PublicSchedule schedule);
	
	
	/**
	 * The FMS would like the GEMS to follow a new schedule [optional -> has to be declined]
	 * 
	 * [!] This callback function has to make sure the schedule isn't lost in case of a restart.
	 * 
	 * The schedule on the server is only then marked as read when this method executes without throwing an exception.
	 * 
	 * @param schedule
	 * @return
	 */
	public void requestSchedule(PublicSchedule schedule);
	
}
