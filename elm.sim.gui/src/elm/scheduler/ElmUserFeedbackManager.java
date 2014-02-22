package elm.scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import elm.hs.api.client.HomeServerPublicApiClient;
import elm.hs.api.model.ElmStatus;
import elm.hs.api.model.ElmUserFeedback;
import elm.util.ClientException;

public class ElmUserFeedbackManager {

	private static final Logger LOG = Logger.getLogger(ElmUserFeedbackManager.class.getName());

	private final Map<String, HomeServerPublicApiClient> deviceFeedbackMap = new HashMap<String, HomeServerPublicApiClient>();

	public synchronized void addFeedbackServer(HomeServerPublicApiClient client, List<String> deviceIds) {
		assert client != null;
		assert deviceIds != null;
		for (String id : deviceIds) {
			// replace previous entries:
			deviceFeedbackMap.put(id, client);
		}
	}

	public synchronized void removeFeedbackServer(HomeServerPublicApiClient client) {
		assert client != null;
		final List<String> toRemove = new ArrayList<String>();
		for (String id : deviceFeedbackMap.keySet()) {
			if (client.equals(deviceFeedbackMap.get(id))) {
				ElmUserFeedback feedback = new ElmUserFeedback(id, ElmStatus.ERROR);
				try {
					client.updateUserFeedback(feedback);
				} catch (ClientException e) {
					LOG.warning("Final user-feedback status notification failed for device: " + id);
				}
				toRemove.add(id);
			}
		}
		for (String id : toRemove) {
			deviceFeedbackMap.remove(id);
		}
	}

	/**
	 * Returns the {@link HomeServerPublicApiClient} to handle the user-feedback for the given device.
	 * 
	 * @param deviceId
	 *            cannot be {@code null}
	 * @return never null {@code null}
	 * @throws IllegalStateException
	 *             if no servers is found to handle the feedback.
	 */
	public HomeServerPublicApiClient getFeedackClient(String deviceId) {
		assert deviceId != null;
		HomeServerPublicApiClient result = deviceFeedbackMap.get(deviceId);
		if (result == null) {
			throw new IllegalStateException("No ELM user-feedback client for device " + deviceId);
		}
		return result;
	}

}
