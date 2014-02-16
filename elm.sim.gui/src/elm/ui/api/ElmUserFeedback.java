package elm.ui.api;

public class ElmUserFeedback {

	/** Device id. */
	public String id;
	public ElmStatus deviceStatus;
	public ElmStatus schedulerStatus;
	public Integer expectedWaitingTimeMillis;

	public ElmUserFeedback() {
		// for GSON
	}
	
	public ElmUserFeedback(ElmStatus schedulerStatus) {
		assert schedulerStatus != null;
		this.schedulerStatus = schedulerStatus;
	}
	
	public ElmUserFeedback(String id, ElmStatus deviceStatus) {
		assert id != null && ! id.isEmpty();
		assert deviceStatus != null;
		this.id = id;
		this.deviceStatus = deviceStatus;
	}
}
