package elm.scheduler.model;

import elm.hs.api.Device;

/**
 * The {@link Device} model (determined via the {@link Device#id}) does not support remote control.
 */
@SuppressWarnings("serial")
public class UnsupportedDeviceModelException extends Exception {

	public UnsupportedDeviceModelException(String deviceId) {
		super(deviceId);
	}
	
}
