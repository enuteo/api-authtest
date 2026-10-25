package bes.tcc.apiauthtest.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.util.RequestUtils;

@Service 
public class CallService {

	private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^/{}]+)\\}");

	private final HttpClient httpClient = HttpClient.newHttpClient();

	public HttpResponse<String> call(
			Endpoint endpoint,
			String baseUrl,
			Map<String, String> pathParameters,
			Map<String, String> queryParameters,
			Map<String, String> headers,
			String body
    ) throws IOException, InterruptedException {

		String endpointPath = RequestUtils.replacePathParameters(endpoint.getUrl(), pathParameters);
		String requestUrl = RequestUtils.joinUrl(baseUrl, endpointPath);
		requestUrl = RequestUtils.appendQueryParameters(requestUrl, queryParameters);

		String method = endpoint.getMethod().toUpperCase(Locale.ROOT);
		
		if (body != null && (method.equals("GET") || method.equals("HEAD"))) {
			throw new IllegalArgumentException(method + " requests cannot have a body");
		}

		HttpRequest.BodyPublisher bodyPublisher = body == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body);
		
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(requestUrl))
				.method(method, bodyPublisher);

		if (headers != null) {
			headers.forEach(requestBuilder::header);
		}

		if (body != null && (
			    headers == null || 
				headers.keySet().stream().noneMatch(
					header -> header.equalsIgnoreCase("Content-Type")
				)
			)
		) {
			requestBuilder.header("Content-Type", "application/json");
		}

		System.out.println("sending request to: " + requestUrl + " with method: " + method + " and body: " + body);
		
		// return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
		return null;

	}

}
