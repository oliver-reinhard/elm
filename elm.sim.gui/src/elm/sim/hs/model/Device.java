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
	// public Setup setup; // not supported yet, see API documentation p. 14
	public List<Log> logs;

	// public List<Error> errors; // not supported yet, see API documentation

	public Error _getError() {
		if (info != null) {
			return info._getError();
		}
		if (status != null) {
			return status._getError();
		}
		return Error.UNKNOWN_ERROR;
	}

	public boolean _isAlive() {
		return _getError().getCode() >= Error.OK.getCode();
	}

	public boolean _isOk() {
		return _getError() == Error.OK;
	}
}
