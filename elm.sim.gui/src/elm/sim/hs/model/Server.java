package elm.sim.hs.model;


/**
 * This class models the {@code server} block of the general, top-level response from the CLAGE Home Server.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Server implements HomeServerObject {

	public String id;
	public short channel;
	public short address;
}
