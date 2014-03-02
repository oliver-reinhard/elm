package elm.scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import elm.hs.api.model.ElmUserFeedback;
import elm.util.ClientException;

public class ElmUserFeedbackManager {

	static class Entry {
		final ElmUserFeedbackClient client;
		boolean hasFeedback;
		final Map<String, ElmUserFeedback> deviceMap = new HashMap<String, ElmUserFeedback>();

		Entry(ElmUserFeedbackClient client) {
			assert client != null;
			this.client = client;
		}

		void putFeedback(String deviceId, ElmUserFeedback feedback) {
			assert deviceId != null;
			assert feedback != null;
			// replace previous entry to prevent ever-growing lists:
			deviceMap.put(deviceId, feedback);
			hasFeedback = true;
		}

		boolean hasFeedback() {
			return hasFeedback;
		}

		/**
		 * Returns the stored {@link ElmUserFeedback} objects and clears the internal store.
		 * 
		 * @return never {@code null} but list may be empty
		 */
		List<ElmUserFeedback> fetchUserFeedback() {
			List<ElmUserFeedback> result = new ArrayList<ElmUserFeedback>(deviceMap.size());
			if (hasFeedback) {
				for (ElmUserFeedback feedback : deviceMap.values()) {
					if (feedback != null) {
						result.add(feedback);
					}
				}
				deviceMap.clear();
				hasFeedback = false;
			}
			return result;
		}

		/**
		 * Used for testing.
		 * 
		 * @param deviceId
		 * @return may be {@code null}
		 */
		ElmUserFeedback getFeedback(String deviceId) {
			return deviceMap.get(deviceId);
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder(Entry.class.getSimpleName());
			b.append("(");
			b.append(client);
			b.append(", [");
			for (ElmUserFeedback f : deviceMap.values()) {
				b.append(f);
				b.append(" ");
			}
			b.append("]");
			return b.toString();
		}
	}

	/** Map <deviceID, Entry>. */
	private final Map<String, Entry> deviceFeedbackMap = new HashMap<String, Entry>();

	/** Reverse map. */
	private final Map<ElmUserFeedbackClient, Entry> clientFeedbackMap = new HashMap<ElmUserFeedbackClient, Entry>();

	public synchronized void addFeedbackServer(ElmUserFeedbackClient client, List<String> deviceIds) {
		assert client != null;
		assert deviceIds != null;
		Entry entry = clientFeedbackMap.get(client); 
		if (entry == null) {
			entry = new Entry(client);
			clientFeedbackMap.put(client, entry);
		}
		for (String id : deviceIds) {
			// replace previous entry:
			deviceFeedbackMap.put(id, entry);
		}
	}

	public synchronized void removeFeedbackServer(ElmUserFeedbackClient client) {
		assert client != null;
		final List<String> toRemove = new ArrayList<String>();
		for (String id : deviceFeedbackMap.keySet()) {
			if (client.equals(deviceFeedbackMap.get(id))) {
				// ElmUserFeedback feedback = new ElmUserFeedback(id, ElmStatus.ERROR);
				// try {
				// client.updateUserFeedback(feedback);
				// } catch (ClientException e) {
				// LOG.warning("Final user-feedback status notification failed for device: " + id);
				// }
				toRemove.add(id);
			}
		}
		for (String id : toRemove) {
			deviceFeedbackMap.remove(id);
		}
		clientFeedbackMap.remove(client);
	}

	public synchronized void putFeedback(ElmUserFeedback feedback) {
		assert feedback != null;
		Entry entry = deviceFeedbackMap.get(feedback.id);
		if (entry == null) {
			throw new IllegalStateException("Feedback for unknown device " + feedback.id);
		}
		// replace previous entries:
		entry.putFeedback(feedback.id, feedback);
	}

	public synchronized void putFeedback(List<ElmUserFeedback> feedback) {
		assert feedback != null;
		for (ElmUserFeedback f : feedback) {
			putFeedback(f);
		}
	}

	/**
	 * 
	 * Fetches the stored {@link ElmUserFeedback} objects to be handled by the given {@code client}, sends the feedback to the server and clears the internal
	 * store.
	 * 
	 * @param client
	 *            cannot be {@code null}
	 * @return never {@code null} but list may be empty
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	public void sendFeedack(ElmUserFeedbackClient client) throws ClientException {
		assert client != null;
		List<ElmUserFeedback> feedback = null;
		synchronized (this) {
			Entry entry = clientFeedbackMap.get(client);
			if (entry == null) {
				throw new IllegalStateException("Unknown user-feedback client " + client);
			}
			feedback = entry.fetchUserFeedback();  // clears the feedback store
		} // release lock
		if (feedback != null) {
			client.updateUserFeedback(feedback);
		}
	}

	/**
	 * Used for testing.
	 * 
	 * @param deviceId
	 *            cannot be {@code null} or empty
	 * @return may be {@code null}
	 */
	public synchronized ElmUserFeedback getFeedback(String deviceId) {
		assert deviceId != null && !deviceId.isEmpty();
		Entry entry = deviceFeedbackMap.get(deviceId);
		assert entry != null;
		return entry.getFeedback(deviceId);

	}

	/** Used for testing. */
	public synchronized boolean hasFeedback(ElmUserFeedbackClient client) {
		assert client != null;
		Entry entry = clientFeedbackMap.get(client);
		if (entry == null) {
			throw new IllegalStateException("Unknown user-feedback client " + client);
		}
		return entry.hasFeedback();
	}
}
