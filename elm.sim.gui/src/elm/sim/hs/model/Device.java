package elm.sim.hs.model;

import java.util.List;

/**
 * This class models the {@code device} object of the CLAGE Home Server API.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Device implements HomeServerObject {

	public String id;
	public short rssi;
	public short lqi;
	public boolean connected;
	public Info info;
	public Status status;
	public Setup setup;
	public List<Log> logs;
	// public List<Error> errors; // not supported yet, see API documentation
}
