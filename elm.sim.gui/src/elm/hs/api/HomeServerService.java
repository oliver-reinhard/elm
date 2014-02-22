package elm.hs.api;

import java.net.URI;

/**
 * This interface represents the service provided by the CLAGE Home Server API v1.0.
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

	/** The default port for internal Home Server access according to API v1.0 documentation. */
	static final int INTERNAL_API_PORT = 8080;

	/** The default URI according to API v1.0 documentation. */
	static final URI INTERNAL_API_URI = URI.create(DEFAULT_URI.toString() + ":" + INTERNAL_API_PORT);

}
