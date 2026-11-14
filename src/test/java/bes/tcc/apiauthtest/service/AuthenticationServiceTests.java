package bes.tcc.apiauthtest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bes.tcc.apiauthtest.dto.AuthenticationConfig;
import bes.tcc.apiauthtest.dto.AuthenticationUser;
import bes.tcc.apiauthtest.dto.Endpoint;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.env.MockEnvironment;

class AuthenticationServiceTests {
	@TempDir
	Path tempDir;
	private HttpServer server;
	private final List<String> loginBodies = new CopyOnWriteArrayList<>();
	private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
	private final List<String> anonymousAuthorizationHeaders = new CopyOnWriteArrayList<>();
	private final List<String> requestPaths = new CopyOnWriteArrayList<>();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/auth/login", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			loginBodies.add(body);
			String username = body.contains("\"yaml-user\"") ? "yaml-user"
					: body.contains("\"yaml-admin\"") ? "yaml-admin" : "yaml-notuser";
			writeResponse(exchange, 200, "{\"access_token\":\"token-" + username
					+ "\",\"token_type\":\"Bearer\",\"expires_in\":300}");
		});
		server.createContext("/object", exchange -> {
			requestPaths.add(exchange.getRequestURI().getPath());
			String authorization = exchange.getRequestHeaders().getFirst("Authorization");
			if (authorization == null) {
				anonymousAuthorizationHeaders.add("<none>");
			} else {
				authorizationHeaders.add(authorization);
			}
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			writeResponse(exchange, authorization == null ? 401 : 200,
					authorization == null ? "{\"error\":\"unauthorized\"}" : "{\"id\":1}");
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void runsEachConfiguredUserAndAnonymousRequestsAndWritesReport() throws Exception {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("AUTHTEST_AUTH_URL", "http://localhost:" + server.getAddress().getPort() + "/auth/login")
				.withProperty("AUTHTEST_USER_PASSWORD", "user-password")
				.withProperty("AUTHTEST_ADMIN_PASSWORD", "admin-password")
				.withProperty("AUTHTEST_NOTUSER_PASSWORD", "notuser-password");
		String usersYaml = """
				authentication:
				  url: "${AUTHTEST_AUTH_URL}"
				  users:
				    - username: yaml-user
				      password: "${AUTHTEST_USER_PASSWORD}"
				      roles: [user, objectrole]
				      objects: [1, 2]
				    - username: yaml-admin
				      password: "${AUTHTEST_ADMIN_PASSWORD}"
				      roles: [admin, objectrole]
				      objects: [2]
				    - username: yaml-notuser
				      password: "${AUTHTEST_NOTUSER_PASSWORD}"
				      roles: []
				      objects: [3]
				""";
		AuthenticationConfigService configService = new AuthenticationConfigService(
				new ByteArrayResource(usersYaml.getBytes(StandardCharsets.UTF_8)), environment);
		AuthenticationConfig config = configService.read();
		EndpointExecutionService executionService = new EndpointExecutionService(
				new AuthenticationService(), new CallService());
		List<Endpoint> endpoints = List.of(
				new Endpoint("/object/{id}", "GET", "getObjectById", "Get object"),
				new Endpoint("/object/{id}", "GET", "getObjectById", "Get another object"));

		Path reportPath = executionService.execute(
				config,
				endpoints,
				"http://localhost:" + server.getAddress().getPort(),
				Map.of("id", "1"),
				tempDir.resolve("authtest-report.txt"));

		assertEquals(List.of(
				new AuthenticationUser("yaml-user", "user-password", List.of("user", "objectrole"), List.of(1L, 2L)),
				new AuthenticationUser("yaml-admin", "admin-password", List.of("admin", "objectrole"), List.of(2L)),
				new AuthenticationUser("yaml-notuser", "notuser-password", List.of(), List.of(3L))),
				config.users());
		assertEquals(3, loginBodies.size());
		assertTrue(loginBodies.get(2).contains("\"yaml-notuser\""));
		assertEquals(8, authorizationHeaders.size());
		assertEquals(2, anonymousAuthorizationHeaders.size());
		assertTrue(anonymousAuthorizationHeaders.stream().allMatch("<none>"::equals));
		assertEquals(List.of("/object/1", "/object/2", "/object/1", "/object/2",
				"/object/2", "/object/2", "/object/3", "/object/3", "/object/1", "/object/1"),
				requestPaths);

		assertTrue(reportPath.getFileName().toString()
				.matches("authtest-report-\\d{8}T\\d{6}\\.\\d{9}Z\\.txt"));
		String report = Files.readString(reportPath);
		assertEquals(8, report.split("HTTP status: 200", -1).length - 1);
		assertEquals(2, report.split("HTTP status: 401", -1).length - 1);
		assertTrue(report.contains("User: yaml-user"));
		assertTrue(report.contains("Roles: user, objectrole"));
		assertTrue(report.contains("User: yaml-notuser"));
		assertTrue(report.contains("Roles: \n"));
		assertTrue(report.contains("Objects: [1, 2]"));
		assertTrue(report.contains("User: (unauthenticated)"));
		assertTrue(report.contains("Authentication: not attempted; no bearer token sent"));
		assertTrue(report.contains("Endpoint: GET /object/{id}"));
		assertTrue(report.contains("{\"id\":1}"));
		assertTrue(report.contains("{\"error\":\"unauthorized\"}"));
		assertTrue(report.contains("content-type: application/json"));

		Path secondReport = executionService.execute(
				config,
				endpoints,
				"http://localhost:" + server.getAddress().getPort(),
				Map.of("id", "1"),
				tempDir.resolve("authtest-report.txt"));
		assertTrue(!reportPath.equals(secondReport));
		try (var reportFiles = Files.list(tempDir)) {
			assertEquals(2, reportFiles.count());
		}
	}

	@Test
	void refusesToCallApiWithoutBearerTokenThroughAuthenticatedCall() {
		assertThrows(IllegalArgumentException.class, () -> new CallService().call(
				new Endpoint("/object/{id}", "GET", "getObjectById", "Get object"),
				"http://localhost:" + server.getAddress().getPort(),
				Map.of("id", "1"),
				null,
				null,
				null,
				null));
	}

	private static void writeResponse(
			com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
		byte[] response = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, response.length);
		try (var output = exchange.getResponseBody()) {
			output.write(response);
		}
	}
}
