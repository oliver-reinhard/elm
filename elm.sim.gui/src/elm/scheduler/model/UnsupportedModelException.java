package elm.scheduler.model;

import elm.hs.api.model.Device;

/**
 * The {@link Device} model (determined via the {@link Device#id}) does not support remote control.
 */
@SuppressWarnings("serial")
public class UnsupportedModelException extends Exception {

	public UnsupportedModelException(String deviceId) {
		super(deviceId);
	}
	
}
