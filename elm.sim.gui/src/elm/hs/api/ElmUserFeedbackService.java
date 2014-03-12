package elm.hs.api;

import java.util.List;

import elm.util.ClientException;

/**
 * This interface defines the methods available to a client that sends {@link ElmUserFeedback}s to a remote server.
 */
public interface ElmUserFeedbackService {

	/**
	 * Queries the server whether is supports user feedback.
	 * 
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	boolean supportsUserFeedback() throws ClientException;

	/**
	 * Returns the device IDs of the devices for which this home server handles the device user feedback.
	 * <p>
	 * <em>Note:this method must only be invoked when {@link #supportsUserFeedback()} returns {@code true}.
	 * </p>
	 * 
	 * @return never {@code null}
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	HomeServerResponse getFeedbackDevices() throws ClientException;

	/**
	 * Sends device user feedback to UI of the respective device.
	 * <p>
	 * <em>Note:this method must only be invoked when {@link #supportsUserFeedback()} returns {@code true}.
	 * </p>
	 * 
	 * @param feedback
	 *            cannot be {@code null} or empty
	 * @throws ClientException
	 *             if the operation ended in a status {@code != 200} or if the execution threw an exception
	 */
	void updateUserFeedback(List<ElmUserFeedback> feedback) throws ClientException;

}