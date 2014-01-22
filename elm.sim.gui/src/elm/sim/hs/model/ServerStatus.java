package elm.sim.hs.model;

import java.util.List;

/**
 * This class models the general, top-level response from the CLAGE Home Server.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class ServerStatus implements HomeServerObject {
	
	public String version;
	public int total;
	public boolean cached;
	public boolean success;
	public short error;
	public int time;
	public List<Device> devices;
	public List<Service> services;
	public Server server;

}
