package elm.hs.api;

import java.util.List;

/**
 * This class models the ELM {@code feedback} object of the Sim Home Server.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Feedback implements HomeServerObject {
	
	/** The device IDs this home server can display feedback information for. */
	public List<String> deviceIds;

}
