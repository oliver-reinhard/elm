package elm.sim.hs.model;

/**
 * This class models the {@code status} object of the CLAGE Home Server.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Status implements HomeServerObject {
	
	public short setpoint;
	public short tIn;
	public short tOut; 
	public short tP1; 
	public short tP2; 
	public short tP3; 
	public short tP4;
	public short flow;
	public short power;
	public short powerMax;
	public short flags;
	public short error;

}
