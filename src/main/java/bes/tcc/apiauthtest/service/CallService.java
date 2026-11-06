package bes.tcc.apiauthtest.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.util.RequestUtils;

@Service 
public class CallService {

	private final HttpClient httpClient = HttpClient.newHttpClient();

	public HttpResponse<String> call(
			Endpoint endpoint,
			String baseUrl,
			Map<String, String> pathParameters,
			Map<String, String> queryParameters,
			Map<String, String> headers,
			String body,
			String bearerToken
    ) throws IOException, InterruptedException {
		if (bearerToken == null || bearerToken.isBlank()) {
			throw new IllegalArgumentException("A bearer token is required to call the API.");
		}
		return callRequest(endpoint, baseUrl, pathParameters, queryParameters, headers, body, bearerToken);
	}

	public HttpResponse<String> callWithoutAuthentication(
			Endpoint endpoint,
			String baseUrl,
			Map<String, String> pathParameters,
			Map<String, String> queryParameters,
			Map<String, String> headers,
			String body
	) throws IOException, InterruptedException {
		return callRequest(endpoint, baseUrl, pathParameters, queryParameters, headers, body, null);
	}

	private HttpResponse<String> callRequest(
			Endpoint endpoint,
			String baseUrl,
			Map<String, String> pathParameters,
			Map<String, String> queryParameters,
			Map<String, String> headers,
			String body,
			String bearerToken
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
			headers.forEach((name, value) -> {
				if (!name.equalsIgnoreCase("Authorization")) {
					requestBuilder.header(name, value);
				}
			});
		}
		if (bearerToken != null) {
			requestBuilder.setHeader("Authorization", "Bearer " + bearerToken);
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

		return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

	}

}
