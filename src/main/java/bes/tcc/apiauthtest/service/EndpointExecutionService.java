package bes.tcc.apiauthtest.service;

import bes.tcc.apiauthtest.dto.AuthenticationConfig;
import bes.tcc.apiauthtest.dto.AuthenticationUser;
import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.dto.ObjectAccessConfig;
import bes.tcc.apiauthtest.dto.ObjectAccessRule;
import bes.tcc.apiauthtest.dto.ObjectInstance;
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
import java.util.Optional;
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
		return execute(config, ObjectAccessConfig.empty(), endpoints, baseUrl, pathParameters, reportPath);
	}

	/**
	 * Executa cada operação com cada usuário. Nas operações com objetos declarados na configuração
	 * de BOLA (objects.yaml), cada usuário chama cada objeto, e o relatório registra o dono e o acesso
	 * esperado. Nas demais operações, usa os parâmetros padrão, como antes.
	 */
	public Path execute(
			AuthenticationConfig config,
			ObjectAccessConfig objectAccess,
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
					Optional<ObjectAccessRule> rule = objectAccess.ruleFor(endpoint);
					if (rule.isEmpty()) {
						callAuthenticated(report, user, token, endpoint, baseUrl, pathParameters, null, null);
						continue;
					}
					for (ObjectInstance object : rule.get().objects()) {
						callAuthenticated(report, user, token, endpoint, baseUrl,
								object.pathParameters(), object.queryParameters(), object);
					}
				}
				report.append('\n');
			}

			report.append("User: (unauthenticated)\n")
					.append("Roles: none\n")
					.append("Authentication: not attempted; no bearer token sent\n");
			for (Endpoint endpoint : endpoints) {
				Optional<ObjectAccessRule> rule = objectAccess.ruleFor(endpoint);
				if (rule.isEmpty()) {
					callUnauthenticated(report, endpoint, baseUrl, pathParameters, null, null);
					continue;
				}
				for (ObjectInstance object : rule.get().objects()) {
					callUnauthenticated(report, endpoint, baseUrl,
							object.pathParameters(), object.queryParameters(), object);
				}
			}
		} finally {
			generatedReport = writeReport(reportPath, runInstant, report.toString());
		}
		return generatedReport;
	}

	private void callAuthenticated(
			StringBuilder report,
			AuthenticationUser user,
			TokenResponse token,
			Endpoint endpoint,
			String baseUrl,
			Map<String, String> pathParameters,
			Map<String, String> queryParameters,
			ObjectInstance object) throws IOException, InterruptedException {
		appendEndpoint(report, endpoint);
		String expected = null;
		if (object != null) {
			expected = expectedAccess(object, user.username());
			appendObject(report, object, expected);
		}
		try {
			HttpResponse<String> response = callService.call(
					endpoint,
					baseUrl,
					pathParameters,
					queryParameters,
					null,
					null,
					token.accessToken());
			appendResponse(report, response, true);
			System.out.printf("User %s (%s) -> %s %s: HTTP %d%s%n",
					user.username(), String.join(",", user.roles()), endpoint.getMethod(),
					endpoint.getUrl(), response.statusCode(), consoleObjectSuffix(object, expected));
		} catch (IOException | IllegalArgumentException exception) {
			report.append("Request: failed\n")
					.append("Error: ").append(exception.getMessage()).append('\n');
			throw exception;
		}
	}

	private void callUnauthenticated(
			StringBuilder report,
			Endpoint endpoint,
			String baseUrl,
			Map<String, String> pathParameters,
			Map<String, String> queryParameters,
			ObjectInstance object) throws IOException, InterruptedException {
		appendEndpoint(report, endpoint);
		String expected = null;
		if (object != null) {
			expected = "DENIED (no credentials)";
			appendObject(report, object, expected);
		}
		try {
			HttpResponse<String> response = callService.callWithoutAuthentication(
					endpoint, baseUrl, pathParameters, queryParameters, null, null);
			appendResponse(report, response, false);
			System.out.printf("User unauthenticated (no roles) -> %s %s: HTTP %d%s%n",
					endpoint.getMethod(), endpoint.getUrl(), response.statusCode(),
					consoleObjectSuffix(object, expected));
		} catch (IOException | IllegalArgumentException exception) {
			report.append("Request: failed\n")
					.append("Error: ").append(exception.getMessage()).append('\n');
			throw exception;
		}
	}

	private String expectedAccess(ObjectInstance object, String username) {
		if (object.isOwner(username)) {
			return "ALLOWED (owner)";
		}
		if (object.hasAccess(username)) {
			return "ALLOWED (shared with user)";
		}
		return "DENIED (not owner)";
	}

	private void appendEndpoint(StringBuilder report, Endpoint endpoint) {
		report.append("\nEndpoint: ").append(endpoint.getMethod()).append(' ')
				.append(endpoint.getUrl()).append('\n');
	}

	private void appendObject(StringBuilder report, ObjectInstance object, String expected) {
		report.append("Object: ").append(object.label())
				.append(" (owner: ").append(object.owner());
		if (!object.sharedWith().isEmpty()) {
			report.append("; shared with: ").append(String.join(", ", object.sharedWith()));
		}
		report.append(")\n")
				.append("Expected access: ").append(expected).append('\n');
	}

	private void appendResponse(StringBuilder report, HttpResponse<String> response, boolean redactAuthorization) {
		report.append("Request URL: ").append(response.uri()).append('\n')
				.append("HTTP version: ").append(response.version()).append('\n')
				.append("HTTP status: ").append(response.statusCode()).append('\n')
				.append("Response headers:\n");
		response.headers().map().forEach((name, values) -> {
			report.append("  ").append(name).append(": ");
			if (name.equalsIgnoreCase("set-cookie")
					|| (redactAuthorization && name.equalsIgnoreCase("authorization"))) {
				report.append("[redacted]\n");
			} else {
				report.append(String.join(", ", values)).append('\n');
			}
		});
		report.append("Response body:\n")
				.append(response.body() == null || response.body().isEmpty()
						? "(empty)\n"
						: response.body() + "\n");
	}

	private String consoleObjectSuffix(ObjectInstance object, String expected) {
		return object == null ? "" : " [object " + object.label() + ", expected " + expected + "]";
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
