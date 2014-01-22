package elm.sim.hs.model;


/**
 * This class models the {@code info} object of the CLAGE Home Server API.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Info implements HomeServerObject {

	public short setpoint;
	public short flags;
	public short error;
	public short access;
	public int activity;
	public String url;
	public short serverCh;
	public short serverAddr;
}
