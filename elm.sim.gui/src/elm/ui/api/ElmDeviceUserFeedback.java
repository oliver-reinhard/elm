package elm.ui.api;

public class ElmDeviceUserFeedback {

	/** Device id. */
	public String id;
	public ElmStatus status;
	public int expectedWaitingTimeMillis;

	public ElmDeviceUserFeedback(String id, ElmStatus status) {
		assert id != null && ! id.isEmpty();
		this.id = id;
		this.status = status;
	}
}
