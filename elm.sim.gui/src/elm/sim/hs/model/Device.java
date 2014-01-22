package elm.sim.hs.model;

import java.util.List;

/**
 * This class models the {@code device} object of the CLAGE Home Server API.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Device implements HomeServerObject {
	
	/** See Home Server API documentation for error numbers. */
	public enum Error {
		
		DEVICE_OK(0), DEVICE_NOT_REGISTERED(-1), DEVICE_NOT_RESPONDING(-3);
		
		final short nr;
		
		private Error(int nr) {
			this.nr = (short) nr;
		}
		
		public short getNumber() {
			return nr;
		}
		
		public boolean equals(short errorNr) {
			return errorNr == nr;
		}
	}

	public String id;
	public short rssi;
	public short lqi;
	public boolean connected;
	public Info info;
	public Status status;
	public Setup setup;
	public List<Log> logs;
	// public List<Error> errors; // not supported yet, see API documentation
	
	public boolean isAlive() {
		if( info != null) {
			return Error.DEVICE_OK.equals(info.error);
		} if (status != null) {
			return Error.DEVICE_OK.equals(status.error);
		}
		return false;
	}
}
