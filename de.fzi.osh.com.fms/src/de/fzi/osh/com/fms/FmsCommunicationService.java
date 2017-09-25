package de.fzi.osh.com.fms;

/**
 * Service for GEMS <-> FMS communication.
 * 
 * [!] The service consumer is responsible for establishing data persistence in case of a crash.
 * 
 * @author K. Foerderer
 *
 */
public interface FmsCommunicationService {
	
	public static enum PublicationType {
		InitialSchedule("schedule_initial.csv"), ScheduleUpdate("schedule_update.csv"), 
		TargetSchedule("schedule_target.csv"), TargetScheduleUpdate("schedule_target_update.csv"),
		ScheduleRequest("schedule_request.csv"), ScheduleRequestDenial("schedule_request_denied.csv");
		
		private String filename;
		
		private PublicationType(String filename) {
			this.filename = filename;
		}
		
		public String getFilename() {
			return filename;
		}
	}	

	/**
	 * Adds a listener for FMS -> GEMS communication.
	 * 
	 * @param listener
	 */
	public void addListener(FmsCommunicationListener listener);
	
	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(FmsCommunicationListener listener);
	
	/**
	 * Send a schedule and the aggregated flexibility to the FMS.
	 * 
	 * @param schedule
	 * @param flexibility
	 * @param type
	 * @return <i>true</i> if the schedule has been published successfully. 
	 */
	public boolean publishSchedule(PublicSchedule schedule, PublicFlexibility flexibility, PublicationType type);
	
	/**
	 * Declines a requested schedule
	 * 
	 * @return <i>true</i> if declining has been successful.
	 */
	public boolean declineScheduleRequest();
}
