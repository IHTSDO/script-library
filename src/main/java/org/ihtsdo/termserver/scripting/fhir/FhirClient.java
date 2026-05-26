package org.ihtsdo.termserver.scripting.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.rest.client.api.*;

import java.io.IOException;
import java.util.List;

public class FhirClient {

	private final FhirContext fhirContext;
	private final IGenericClient client;
	private final String serverUrl;
	private final RedirectCapturingInterceptor redirectInterceptor = new RedirectCapturingInterceptor();

	public FhirClient(String serverUrl, FhirContext fhirContext) {
		this(serverUrl, fhirContext, null);
	}

	public FhirClient(String serverUrl, FhirContext fhirContext, String cookie) {
		this.serverUrl = serverUrl;
		this.fhirContext = fhirContext;
		fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		fhirContext.setParserErrorHandler(new LenientErrorHandler(false));
		this.client = fhirContext.newRestfulGenericClient(serverUrl);
		client.registerInterceptor(redirectInterceptor);
		if (cookie != null) {
			client.registerInterceptor(new CookieInterceptor(cookie));
		}
	}

	public IGenericClient getClient() {
		return client;
	}

	public FhirContext getFhirContext() {
		return fhirContext;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	/** Returns "METHOD url" for the most recent request (e.g. "POST https://server/fhir/CodeSystem/$lookup"). */
	public String getLastRequestDetail() {
		return redirectInterceptor.lastRequestDetail;
	}

	/** Returns the request body of the most recent request, or null for GET requests. */
	public String getLastRequestBody() {
		return redirectInterceptor.lastRequestBody;
	}

	/** Returns the Location header from the most recent 3xx response, or null if there was none. */
	public String getLastRedirectLocation() {
		return redirectInterceptor.lastRedirectLocation;
	}

	private static class RedirectCapturingInterceptor implements IClientInterceptor {
		private volatile String lastRequestDetail;
		private volatile String lastRequestBody;
		private volatile String lastRedirectLocation;

		@Override
		public void interceptRequest(IHttpRequest theRequest) {
			lastRequestDetail = theRequest.getHttpVerbName() + " " + theRequest.getUri();
			try {
				lastRequestBody = theRequest.getRequestBodyFromStream();
			} catch (IOException e) {
				lastRequestBody = null;
			}
			lastRedirectLocation = null;
		}

		@Override
		public void interceptResponse(IHttpResponse theResponse) throws IOException {
			int status = theResponse.getStatus();
			if (status >= 300 && status < 400) {
				List<String> locations = theResponse.getHeaders("Location");
				lastRedirectLocation = locations.isEmpty() ? null : locations.get(0);
			}
		}
	}

	private record CookieInterceptor(String cookie) implements IClientInterceptor {
		@Override
		public void interceptRequest(IHttpRequest theRequest) {
			theRequest.addHeader("Cookie", cookie);
		}

		@Override
		public void interceptResponse(IHttpResponse theResponse) throws IOException {
		}
	}
}
