package elm.scheduler;

import java.util.List;

import elm.hs.api.model.ElmUserFeedback;
import elm.hs.api.model.HomeServerResponse;
import elm.util.ClientException;

/**
 * This interface defines the methods of a client used to send {@link ElmUserFeedback}s to a remote server.
 */
public interface ElmUserFeedbackClient {

	/**
	 * Queries the server whether is supports user feedback.
	 * <p>
	 * <em>Note: </em>{@link #getFeedbackDevices()} and {@link #updateUserFeedback(ElmUserFeedback)} must only be invoked when this method returns {@code true}.
	 * </p>
	 * 
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	boolean supportsUserFeedback() throws ClientException;

	/**
	 * Returns the device IDs of the devices for which this home server handles the device user feedback.
	 * 
	 * @return never {@code null}
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	HomeServerResponse getFeedbackDevices() throws ClientException;

	/**
	 * Sends device user feedback to UI of the respective device.
	 * 
	 * @param feedback
	 *            cannot be {@code null} or empty
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	void updateUserFeedback(List<ElmUserFeedback> feedback) throws ClientException;

}