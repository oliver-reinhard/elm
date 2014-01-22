package elm.sim.hs;

import java.net.URI;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BasicAuthentication;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import elm.sim.hs.model.ServerStatus;

public class HomeServerTestClient {

	private static final String HS_URI = "http://192.168.204.204";

	public static void main(String[] args) {

		String user = "admin";
		String pass = "geheim";
		//
		// BUG (jetty 9.0.0): the HttpClient simply FORGETS to put the authentication header into the request.
		//
		// Replace AuthenticationHttpClient by HttpClient once the bug has been fixed and the header is correct
		//
		HttpClient client = new AuthenticatingHttpClient(user, pass);
		String responseAsString = null;
		try {

			// The following lines are without effect due to the above-mentioned BUG:
			URI uri = new URI(HS_URI);
			String realm = "MyRealm";
			AuthenticationStore authStore = client.getAuthenticationStore();
			authStore.addAuthentication(new BasicAuthentication(uri, realm, user, pass));

			client.start();
			// ContentResponse response = client.GET("http://localhost:8080/hs?action");
			ContentResponse response = client.GET(HS_URI + "/devices/status/A001FFFF8A");
			responseAsString = response.getContentAsString();
			System.out.println("Response as String = " + responseAsString);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Object obj = gson.fromJson(responseAsString, ServerStatus.class);
			System.out.println(obj.getClass().getName() + ": " + gson.toJson(obj));

		} catch (Exception e) {
			if (responseAsString != null) {
				System.out.println("EXCEPTION: Response as String = " + responseAsString);
			}
			e.printStackTrace();
		} finally {
			try {
				client.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
