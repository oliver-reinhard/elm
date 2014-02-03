package elm.hs.api.client;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpStatus;

@SuppressWarnings("serial")
public class ClientException extends Exception {

	public enum Error {
		/** The server has responded with a {@link HttpStatus} status that signals the operation was not successful from an application point of view. */
		APPLICATION_FAILURE_RESPONSE,
		/** The server application has encountered a timeout. */
		APPLICATION_TIMEOUT,
		/** The information received from the server is inconsistent or not as expected. */
		APPLICATION_DATA_ERROR,
		/** No HTTP response was delivered within the given time frame. */
		NETWORK_TIMEOUT,
		/** The execution thread was interrupted by the application. */
		INTERRUPTED, 
		/** A problem has arisen at a technical layer (not an application layer). */ 
		EXECUTION_ERROR
	}

	private final Error error;
	private final long time;

	public ClientException(Error error) {
		this(error, null);
	}

	/**
	 * @param cause see {@link HttpClient#GET(String)}
	 */
	public ClientException(InterruptedException cause) {
		this(Error.INTERRUPTED, cause);
	}

	/**
	 * @param cause see {@link HttpClient#GET(String)}
	 */
	public ClientException(ExecutionException cause) {
		this(Error.EXECUTION_ERROR, cause);
	}
	
	/**
	 * @param cause see {@link HttpClient#GET(String)}
	 */
	public ClientException(TimeoutException cause) {
		this(Error.NETWORK_TIMEOUT, cause);
	}

	protected ClientException(Error error, Throwable cause) {
		super(cause);
		assert error != null;
		this.error = error;
		this.time = System.currentTimeMillis();
	}

	public Error getError() {
		return error;
	}

	public long getOccurrenceTimeMillis() {
		return time;
	}

}
