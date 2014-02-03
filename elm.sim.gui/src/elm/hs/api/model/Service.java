package elm.hs.api.model;

/**
 * This class models the {@code service} object of the CLAGE Home Server.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Service implements HomeServerObject {

	public static final String DEVICES_PATH = "/devices";
	public static final String STATUS_PATH = "/devices/status";
	public static final String SETPOINT_PATH = "/devices/setpoint";
	public static final String LOGS_PATH = "/devices/logs";
	public static final String FILES_PATH = "/files";
	public static final String TIMERS_PATH = "/timers";
	
	public String deviceList;
	public String deviceStatus;
	public String deviceSetpoint;
	// public String deviceSetup; // not supported yet, see API documentation
	// public String deviceErrors;// not supported yet, see API documentation
	public String deviceLogs;
	public String fileList;
	public String timerList;

}
