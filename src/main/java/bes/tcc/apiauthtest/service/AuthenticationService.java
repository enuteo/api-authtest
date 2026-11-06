package bes.tcc.apiauthtest.service;

import bes.tcc.apiauthtest.dto.AuthenticationConfig;
import bes.tcc.apiauthtest.dto.AuthenticationUser;
import bes.tcc.apiauthtest.dto.LoginRequest;
import bes.tcc.apiauthtest.dto.TokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient;

	public AuthenticationService() {
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	public TokenResponse authenticate(AuthenticationConfig config, AuthenticationUser user)
			throws IOException, InterruptedException {
		String requestBody = objectMapper.writeValueAsString(new LoginRequest(user.username(), user.password()));
		HttpRequest request = HttpRequest.newBuilder(URI.create(config.url()))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("Authentication API returned HTTP " + response.statusCode() + ".");
		}

		TokenResponse tokenResponse;
		try {
			tokenResponse = objectMapper.readValue(response.body(), TokenResponse.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Authentication API returned an invalid token response.", exception);
		}
		if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
			throw new IllegalStateException("Authentication API response did not contain an access token.");
		}
		return tokenResponse;
	}
}
