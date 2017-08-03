package de.fzi.osh.wamp.schedule;

import de.fzi.osh.types.flexibilities.Flexibility;

/**
 * Response for a flexibility request.
 * 
 * @author K. Foerderer
 *
 */
public class GetFlexibilityResponse {
	/**
	 * Flexibility that has been asked for.
	 */
	public Flexibility flexibility;
}
