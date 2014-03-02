package elm.hs.api.model;


public class ElmUserFeedback {

	/** Device id. */
	public String id;

	/** Cannot be {@code null}. */
	public ElmStatus deviceStatus;

	public int expectedWaitingTimeMillis;

	public ElmUserFeedback() {
		// for GSON
	}

	public ElmUserFeedback(String deviceId, ElmStatus deviceStatus, Integer expectedWaitingTimeMillis) {
		assert deviceId != null && !deviceId.isEmpty();
		assert deviceStatus != null;
		this.id = deviceId;
		this.deviceStatus = deviceStatus;
		this.expectedWaitingTimeMillis = expectedWaitingTimeMillis;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (obj instanceof ElmUserFeedback) {
			ElmUserFeedback other = (ElmUserFeedback) obj;
			return id.equals(other.id) && deviceStatus == other.deviceStatus && expectedWaitingTimeMillis == other.expectedWaitingTimeMillis;
		}
		return false;
	}
	
	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder("(");
		b.append(id);
		b.append(", ");
		b.append(deviceStatus);
		b.append(", ");
		b.append(expectedWaitingTimeMillis);
		b.append("ms");
		b.append(")");
		return b.toString();
	}
}
