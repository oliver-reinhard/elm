package elm.hs.api.model;

/**
 * This class models the {@code setup} object of the CLAGE Home Server.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Setup implements HomeServerObject {
	public String swVersion;
	public String serialDevice;
	public String serialPowerUnit;
	public short flowMax;
	public short loadShedding;
	public int scaldProtection ; // this should be an unsigned 16 bit integer
	public short fcpAddr;
	public short powerCosts;
	public short powerMax;
	public int  calValue;
	public int  timerPowerOn;
	public int  timerLifetime;
	public int  timerStandby;
	public short   totalPowerConsumption;
	public short totalWaterConsumption;

}
