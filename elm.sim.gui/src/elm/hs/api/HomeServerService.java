package elm.hs.api;

import java.net.URI;

import elm.util.ClientException;

/**
 * This interface represents those elements of the service provided by the CLAGE Home Server API v1.0 and the simulated Home Server as used by ELM.
 * 
 * @see HomeServerInternalService
 * @see ElmUserFeedbackService
 */
public interface HomeServerService {

	/** The DNS Service Discovery Type of the Home Server. */
	static final String DNS_SD_HS_SERVICE_TYPE = "_clage-hs._tcp.local.";

	/** Name under which the {@link SimHomeServerService} registers itself in the Service Discovery registry. */
	static final String DNS_SD_HS_SIM_SERVICE_NAME = "Sim Home Server";

	/** The default URI according to API v1.0 documentation. */
	static final URI DEFAULT_URI = URI.create("https://192.168.204.204");

	/** The Home Server administration user according to API v1.0 documentation. */
	static final String ADMIN_USER = "admin";

	/** The Home Server administration user password according to API v1.0 documentation. */
	static final String DEFAULT_PASSWORD = "geheim";

	/**
	 * @return never {@code null}
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	HomeServerResponse getServerStatus() throws ClientException;

	/**
	 * Initializes a device discovery and an update of the its device list at the Home Server.
	 * <p>
	 * <b>Note: </b>this method does not need to be called. Discovery is used before new devices can be registered at the Home Server.
	 * </p>
	 * <p>
	 * <b>Note 2: </b>the discovery process, i.e. the invocation of this method, blocks out other calls for up to 10 seconds; the devices list may be incomplete
	 * before that period has expired.
	 * </p>
	 * 
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 202} or if the execution threw an exception
	 */
	void discoverDevices() throws ClientException;

	/**
	 * Returns all devices registered at this Home Server, regardless of whether they are currently turned on or off.
	 * 
	 * @return never {@code null}
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	HomeServerResponse getRegisteredDevices() throws ClientException;

	/**
	 * Returns all devices that the Home Server ever contacted since its last reboot. This includes devices that are not registered at this Home Server.
	 * <p>
	 * <b>Note: </b>this method does not need to be called. It would be used to (manually) register new devices at the Home Server.
	 * </p>
	 * <p>
	 * 
	 * @return never {@code null}
	 * @throws ClientException
	 */
	HomeServerResponse getAllDevices() throws ClientException;

	/**
	 * Gets the {@link Status} information for the given device.
	 * <p>
	 * <em>Note: </em>This method only succeeds for devices that have been {@link #manageDevice(String) added} as a managed device.
	 * </p>
	 * 
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return never {@code null}
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	HomeServerResponse getDeviceStatus(String deviceID) throws ClientException;

	/**
	 * Configures a device as managed by this Home Server.
	 * 
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	void manageDevice(String deviceID) throws ClientException;

	/**
	 * Frees a device from being managed this Home Server.
	 * 
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	void unmanageDevice(String deviceID) throws ClientException;

	/**
	 * Returns the current reference temperature for the given device.
	 * 
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @return temperature in [1/10°C]
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	short getReferenceTemperature(String deviceID) throws ClientException;

	/**
	 * Sets the reference temperature (a.k.a <em>setpoint</em)> for the given device.
	 * 
	 * @param newTemp
	 *            in [1/10°C], cannot be {@code < 0}
	 * @param deviceID
	 *            cannot be {@code null} or empty
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	void setReferenceTemperature(String deviceID, int newTemp) throws ClientException;
}
