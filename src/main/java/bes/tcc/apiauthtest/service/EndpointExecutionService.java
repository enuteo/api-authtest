package bes.tcc.apiauthtest.service;

import bes.tcc.apiauthtest.dto.AuthenticationConfig;
import bes.tcc.apiauthtest.dto.AuthenticationUser;
import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.dto.TokenResponse;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EndpointExecutionService {
	private static final DateTimeFormatter REPORT_TIMESTAMP =
			DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

	private final AuthenticationService authenticationService;
	private final CallService callService;

	public EndpointExecutionService(AuthenticationService authenticationService, CallService callService) {
		this.authenticationService = authenticationService;
		this.callService = callService;
	}

	public Path execute(
			AuthenticationConfig config,
			List<Endpoint> endpoints,
			String baseUrl,
			Map<String, String> pathParameters,
			Path reportPath) throws IOException, InterruptedException {
		Instant runInstant = Instant.now();
		StringBuilder report = new StringBuilder()
				.append("API Authtest execution report\n")
				.append("Generated: ")
				.append(OffsetDateTime.ofInstant(runInstant, ZoneId.systemDefault())).append('\n')
				.append("Base URL: ").append(baseUrl).append("\n\n");
		Path generatedReport;

		try {
			for (AuthenticationUser user : config.users()) {
				report.append("User: ").append(user.username()).append('\n')
						.append("Roles: ").append(String.join(", ", user.roles())).append('\n');

				TokenResponse token;
				try {
					token = authenticationService.authenticate(config, user);
					report.append("Authentication: successful\n");
				} catch (IOException | IllegalStateException exception) {
					report.append("Authentication: failed\n")
							.append("Error: ").append(exception.getMessage()).append("\n\n");
					throw exception;
				}

				for (Endpoint endpoint : endpoints) {
					report.append("\nEndpoint: ").append(endpoint.getMethod()).append(' ')
							.append(endpoint.getUrl()).append('\n');
					try {
						HttpResponse<String> response = callService.call(
								endpoint,
								baseUrl,
								pathParameters,
								null,
								null,
								null,
								token.accessToken());
						report.append("Request URL: ").append(response.uri()).append('\n')
								.append("HTTP version: ").append(response.version()).append('\n')
								.append("HTTP status: ").append(response.statusCode()).append('\n')
								.append("Response headers:\n");
						response.headers().map().forEach((name, values) -> {
							report.append("  ").append(name).append(": ");
							if (name.equalsIgnoreCase("set-cookie")
									|| name.equalsIgnoreCase("authorization")) {
								report.append("[redacted]\n");
							} else {
								report.append(String.join(", ", values)).append('\n');
							}
						});
						report.append("Response body:\n")
								.append(response.body() == null || response.body().isEmpty()
										? "(empty)\n"
										: response.body() + "\n");
						System.out.printf("User %s (%s) -> %s %s: HTTP %d%n",
								user.username(), String.join(",", user.roles()), endpoint.getMethod(),
								endpoint.getUrl(), response.statusCode());
					} catch (IOException | IllegalArgumentException exception) {
						report.append("Request: failed\n")
								.append("Error: ").append(exception.getMessage()).append('\n');
						throw exception;
					}
				}
				report.append('\n');
			}

			report.append("User: (unauthenticated)\n")
					.append("Roles: none\n")
					.append("Authentication: not attempted; no bearer token sent\n");
			for (Endpoint endpoint : endpoints) {
				report.append("\nEndpoint: ").append(endpoint.getMethod()).append(' ')
						.append(endpoint.getUrl()).append('\n');
				try {
					HttpResponse<String> response = callService.callWithoutAuthentication(
							endpoint, baseUrl, pathParameters, null, null, null);
					report.append("Request URL: ").append(response.uri()).append('\n')
							.append("HTTP version: ").append(response.version()).append('\n')
							.append("HTTP status: ").append(response.statusCode()).append('\n')
							.append("Response headers:\n");
					response.headers().map().forEach((name, values) -> report
							.append("  ").append(name).append(": ")
							.append(name.equalsIgnoreCase("set-cookie")
									? "[redacted]\n"
									: String.join(", ", values) + "\n"));
					report.append("Response body:\n")
							.append(response.body() == null || response.body().isEmpty()
									? "(empty)\n"
									: response.body() + "\n");
					System.out.printf("User unauthenticated (no roles) -> %s %s: HTTP %d%n",
							endpoint.getMethod(), endpoint.getUrl(), response.statusCode());
				} catch (IOException | IllegalArgumentException exception) {
					report.append("Request: failed\n")
							.append("Error: ").append(exception.getMessage()).append('\n');
					throw exception;
				}
			}
		} finally {
			generatedReport = writeReport(reportPath, runInstant, report.toString());
		}
		return generatedReport;
	}

	private Path writeReport(Path configuredPath, Instant runInstant, String content) throws IOException {
		Path absolutePath = configuredPath.toAbsolutePath();
		Path parent = absolutePath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		for (int collision = 0; ; collision++) {
			Path candidate = timestampedPath(absolutePath, runInstant, collision);
			try {
				Files.writeString(candidate, content, StandardCharsets.UTF_8,
						StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				return candidate;
			} catch (FileAlreadyExistsException exception) {
				// Add a suffix if two executions happen to share the same timestamp.
			}
		}
	}

	private Path timestampedPath(Path configuredPath, Instant runInstant, int collision) {
		String fileName = configuredPath.getFileName().toString();
		int extensionStart = fileName.lastIndexOf('.');
		String name = extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
		String extension = extensionStart > 0 ? fileName.substring(extensionStart) : "";
		String suffix = collision == 0 ? "" : "-" + collision;
		String timestampedName = name + "-" + REPORT_TIMESTAMP.format(runInstant) + suffix + extension;
		return configuredPath.resolveSibling(timestampedName);
	}
}
