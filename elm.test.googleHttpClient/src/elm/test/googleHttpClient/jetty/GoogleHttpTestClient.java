package elm.test.googleHttpClient.jetty;

import java.io.IOException;
import java.net.URL;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;

public class GoogleHttpTestClient {

	static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	static final JsonFactory JSON_FACTORY = new JacksonFactory();

	private static void parseResponse(HttpResponse response) throws IOException {
		Member2 member = response.parseAs(Member2.class);
		System.out.println("Member " + member.getName());
	}

	private static void run() throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
			@Override
			public void initialize(HttpRequest request) {
				request.setParser(new JsonObjectParser(JSON_FACTORY));
			}
		});
		HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(new URL("http://localhost:8080/chat?action")));
		parseResponse(request.execute());
	}

	public static void main(String[] args) {
		try {
			try {
				run();
				return;
			} catch (HttpResponseException e) {
				System.err.println(e.getMessage());
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		System.exit(1);
	}
}
