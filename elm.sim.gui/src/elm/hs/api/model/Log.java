package elm.hs.api.model;

/**
 * This class models the {@code log} object of the CLAGE Home Server.
 * 
 * @see HomeServerObject Please read this note regarding the absence of accessor methods.
 */
public class Log implements HomeServerObject {

	public int id;
	public int time;
	public int length;
	public int power;
	public int water;
	public int cid;

}
