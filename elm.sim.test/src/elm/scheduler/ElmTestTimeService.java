package elm.scheduler;

/**
 * A time service with a deterministic time model in order to have consistent timing across different runs of the same test, even on different hardware and
 * system load.
 */
public class ElmTestTimeService extends ElmTimeService {

	private long time = 0L;
	private boolean stopped = false;

	@Override
	public synchronized long currentTimeMillis() {
		if (! stopped) {
			advanceTime(1);
		}
		return time;
	}
	
	public synchronized void advanceTime(long millis) {
		time += millis;
	}
	
	public synchronized void setStopped(boolean value) {
		stopped = value;
	}
}
