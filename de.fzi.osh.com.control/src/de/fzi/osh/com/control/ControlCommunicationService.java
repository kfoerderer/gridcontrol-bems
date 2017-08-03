package de.fzi.osh.com.control;

/**
 * Service for GEMS <-> VNB communication.
 * 
 * [!] The service consumer is responsible for establishing data persistence in case of a crash.
 * 
 * @author K. Foerderer
 *
 */
public interface ControlCommunicationService {
	
	public static enum PublicationType {
		Execute("execute.csv");
		
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
	public void addListener(ControlCommunicationListener listener);
	
	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(ControlCommunicationListener listener);
	
	/**
	 * Publish a file containing the given message string.
	 * 
	 * @param message
	 */
	public void publishMessage(String message);
}
