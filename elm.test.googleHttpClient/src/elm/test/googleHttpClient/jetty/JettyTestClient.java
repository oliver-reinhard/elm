package elm.test.googleHttpClient.jetty;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JettyTestClient {

	public static void main(String[] args) {

		HttpClient httpClient = new HttpClient();
		try {
			httpClient.start();
			ContentResponse response = httpClient.GET("http://localhost:8080/chat?action");
			String str = response.getContentAsString();
			System.out.println("Str = " + str);
			Gson gson = new GsonBuilder().create();
			Member member = gson.fromJson(str, Member.class);
			System.out.println("Member " + member.getName());
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
