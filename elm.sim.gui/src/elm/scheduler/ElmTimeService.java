package elm.scheduler;

/**
 * A time service based on {@link System#currentTimeMillis()}. This is overridden for scheduler testing in order to have deterministic timing for waiting time estimates,
 * etc.
 */
public class ElmTimeService {
	
	public static final ElmTimeService INSTANCE = new ElmTimeService();
	
	protected ElmTimeService() {
		// prevent instantiation from outide this package
	}

	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}
}
