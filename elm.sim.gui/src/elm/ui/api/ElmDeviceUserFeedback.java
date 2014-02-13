package elm.ui.api;

public class ElmDeviceUserFeedback {

	/** Device id. */
	public String id;
	public ElmStatus deviceStatus;
	public ElmStatus schedulerStatus;
	public int expectedWaitingTimeMillis;

	public ElmDeviceUserFeedback(String id, ElmStatus deviceStatus) {
		assert id != null && ! id.isEmpty();
		this.id = id;
		this.deviceStatus = deviceStatus;
	}
}
