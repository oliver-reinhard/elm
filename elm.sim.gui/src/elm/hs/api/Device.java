package elm.hs.api;

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
	
	public void setSetpoint(short setpoint) {
		if (info != null) {
			info.setpoint = setpoint;
		}
		if (status != null) {
			status.setpoint = setpoint;
		}
	}
	
	public short _getSetpoint() {
		if (info != null) {
			return info.setpoint;
		}
		if (status != null) {
			return status.setpoint;
		}
		throw new IllegalStateException();
	}
	
	public void setHeaterOn(boolean on) {
		final short flags = (short) (on ? 0 : 1);
		if (info != null) {
			info.flags = flags;
		}
		if (status != null) {
			status.flags = flags;
		}
	}
	
	public boolean _isHeaterOn() {
		if (info != null) {
			return info.flags == 0;
		}
		if (status != null) {
			return status.flags ==0;
		}
		throw new IllegalStateException();
	}
}
