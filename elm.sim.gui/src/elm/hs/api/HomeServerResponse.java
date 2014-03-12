package elm.hs.api;

import java.util.List;

/**
 * This class models the general, top-level response from the CLAGE Home Server.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class HomeServerResponse implements HomeServerObject {
	
	public String version;
	public int total;
	public boolean cached;
	public boolean success;
	public short error;
	public int time;
	public List<Device> devices;
	public List<Service> services;
	public Server server;
	public Response response;
	
	// Sim Home Server only:
	public Feedback feeback;

	public boolean _isDeviceAlive(String deviceID) {
		assert deviceID != null & ! deviceID.isEmpty();
		
		if (devices == null) {
			return false;
		}
		for (Device device : devices) {
			if (deviceID.equals(device.id)) {
				return device._isAlive();
			}
		}
		return false;
	}
	
	public Error _getError() {
		return Error.fromCode(error);
	}
}
