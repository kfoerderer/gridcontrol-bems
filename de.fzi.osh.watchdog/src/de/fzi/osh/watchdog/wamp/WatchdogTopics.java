package de.fzi.osh.watchdog.wamp;

/**
 * Interface for crossbar monitoring.
 * 
 * @author Foerderer K.
 *
 */
public class WatchdogTopics {

	private String prefix;
	
	/**
	 * Constructor.
	 * 
	 * @param prefix
	 */
	public WatchdogTopics(String prefix) {
		this.prefix = prefix;
	}
	
	
	/**
	 * Echo functionality.
	 * 
	 * @return
	 */
	public String echo() {
		return prefix + ".watchdog.echo";
	}
}
