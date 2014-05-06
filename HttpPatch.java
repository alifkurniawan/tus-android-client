
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

public class HttpPatch extends HttpEntityEnclosingRequestBase {
	public final static String METHOD_NAME = "PATCH";

	public HttpPatch() {
		super();
	}

	public HttpPatch(String url) {

		URI uri = null;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		super.setURI(uri);
	}

	public HttpPatch(URI uri) {
		super.setURI(uri);
	}

	@Override
	public String getMethod() {
		// TODO Auto-generated method stub
		return METHOD_NAME;
	}
}
