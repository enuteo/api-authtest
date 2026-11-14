package bes.tcc.apiauthtest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bes.tcc.apiauthtest.dto.AuthenticationConfig;
import bes.tcc.apiauthtest.dto.AuthenticationUser;
import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.dto.ObjectAccessConfig;
import bes.tcc.apiauthtest.dto.ObjectAccessRule;
import bes.tcc.apiauthtest.dto.ObjectInstance;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

class ObjectAccessConfigServiceTests {
	@TempDir
	Path tempDir;

	private final AuthenticationConfig authentication = new AuthenticationConfig(
			"http://localhost/auth/login",
			List.of(
					new AuthenticationUser("user", "p1", List.of("user", "objectrole")),
					new AuthenticationUser("admin", "p2", List.of("admin", "objectrole")),
					new AuthenticationUser("notuser", "p3", List.of())));

	private final List<Endpoint> endpoints = List.of(
			new Endpoint("/object/{id}", "GET", "getObjectById", "Get object"),
			new Endpoint("/report", "GET", "getReport", "Get report"));

	private ObjectAccessConfig read(String yaml) throws IOException {
		return read(yaml, new MockEnvironment());
	}

	private ObjectAccessConfig read(String yaml, MockEnvironment environment) throws IOException {
		return new ObjectAccessConfigService(
				new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)), environment)
				.read(authentication, endpoints);
	}

	@Test
	void readsOwnersParametersAndSharedUsers() throws IOException {
		ObjectAccessConfig config = read("""
				objects:
				  - operation: get /object/{id}
				    instances:
				      - id: obj-user
				        owner: "${OWNER:user}"
				        pathParameters:
				          id: 1
				      - owner: admin
				        sharedWith: [user]
				        pathParameters:
				          id: "2"
				  - operation: GET /report
				    instances:
				      - owner: admin
				        queryParameters:
				          report_id: 7
				""");

		ObjectAccessRule objectRule = config.ruleFor(endpoints.get(0)).orElseThrow();
		assertEquals("GET /object/{id}", objectRule.operation());
		assertEquals(2, objectRule.objects().size());

		ObjectInstance first = objectRule.objects().get(0);
		assertEquals("obj-user", first.label());
		assertEquals("user", first.owner());
		assertEquals(Map.of("id", "1"), first.pathParameters());
		assertTrue(first.hasAccess("user"));
		assertFalse(first.hasAccess("admin"));

		ObjectInstance second = objectRule.objects().get(1);
		assertEquals("object-2", second.label());
		assertTrue(second.isOwner("admin"));
		assertTrue(second.hasAccess("user"));
		assertFalse(second.isOwner("user"));
		assertFalse(second.hasAccess("notuser"));

		ObjectInstance report = config.ruleFor(endpoints.get(1)).orElseThrow().objects().get(0);
		assertEquals(Map.of("report_id", "7"), report.queryParameters());
	}

	@Test
	void returnsEmptyConfigWhenFileDoesNotExist() throws IOException {
		ObjectAccessConfig config = new ObjectAccessConfigService(
				new FileSystemResource(tempDir.resolve("missing.yaml")), new MockEnvironment())
				.read(authentication, endpoints);
		assertTrue(config.rules().isEmpty());
	}

	@Test
	void rejectsUnknownOwner() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: GET /object/{id}
				    instances:
				      - owner: ghost
				        pathParameters: {id: "1"}
				"""));
		assertTrue(error.getMessage().contains("ghost"));
	}

	@Test
	void rejectsUnknownSharedUser() {
		assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: GET /object/{id}
				    instances:
				      - owner: user
				        sharedWith: [ghost]
				        pathParameters: {id: "1"}
				"""));
	}

	@Test
	void rejectsOperationMissingFromOpenApi() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: DELETE /object/{id}
				    instances:
				      - owner: user
				        pathParameters: {id: "1"}
				"""));
		assertTrue(error.getMessage().contains("not declared in the OpenAPI"));
	}

	@Test
	void rejectsMissingOrExtraPathParameters() {
		assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: GET /object/{id}
				    instances:
				      - owner: user
				"""));
		assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: GET /object/{id}
				    instances:
				      - owner: user
				        pathParameters: {id: "1", other: "2"}
				"""));
	}

	@Test
	void rejectsDuplicateOperationsAndObjectIds() {
		assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: GET /object/{id}
				    instances:
				      - owner: user
				        pathParameters: {id: "1"}
				  - operation: GET /object/{id}
				    instances:
				      - owner: admin
				        pathParameters: {id: "2"}
				"""));
		assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: GET /object/{id}
				    instances:
				      - id: same
				        owner: user
				        pathParameters: {id: "1"}
				      - id: same
				        owner: admin
				        pathParameters: {id: "2"}
				"""));
	}

	@Test
	void rejectsBadOperationFormatAndEmptyInstances() {
		assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: /object/{id}
				    instances:
				      - owner: user
				        pathParameters: {id: "1"}
				"""));
		assertThrows(IllegalArgumentException.class, () -> read("""
				objects:
				  - operation: GET /object/{id}
				    instances: []
				"""));
	}

	@Test
	void callsEachObjectWithEachUserAndReportsExpectedAccess() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/auth/login", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String username = body.contains("\"admin\"") ? "admin" : body.contains("\"notuser\"") ? "notuser" : "user";
			writeResponse(exchange, 200, "{\"access_token\":\"token-" + username + "\"}");
		});
		// Simula uma API vulnerável a BOLA: devolve qualquer objeto a qualquer usuário autenticado.
		server.createContext("/object", exchange -> {
			String authorization = exchange.getRequestHeaders().getFirst("Authorization");
			String id = exchange.getRequestURI().getPath().replace("/object/", "");
			writeResponse(exchange, authorization == null ? 401 : 200, "{\"id\":" + id + "}");
		});
		server.start();
		try {
			String base = "http://localhost:" + server.getAddress().getPort();
			AuthenticationConfig config = new AuthenticationConfig(base + "/auth/login", authentication.users());
			ObjectAccessConfig objects = new ObjectAccessConfigService(
					new ByteArrayResource("""
							objects:
							  - operation: GET /object/{id}
							    instances:
							      - id: obj-user
							        owner: user
							        pathParameters: {id: "1"}
							      - id: obj-admin
							        owner: admin
							        pathParameters: {id: "2"}
							""".getBytes(StandardCharsets.UTF_8)),
					new MockEnvironment()).read(config, endpoints.subList(0, 1));

			Path reportPath = new EndpointExecutionService(new AuthenticationService(), new CallService())
					.execute(config, objects, endpoints.subList(0, 1), base, Map.of("id", "99"),
							tempDir.resolve("authtest-report.txt"));
			String report = Files.readString(reportPath);

			// 3 usuários autenticados + 1 anônimo, cada um chamando os 2 objetos.
			assertEquals(8, count(report, "Object: "));
			assertEquals(2, count(report, "Expected access: ALLOWED (owner)"));
			assertEquals(4, count(report, "Expected access: DENIED (not owner)"));
			assertEquals(2, count(report, "Expected access: DENIED (no credentials)"));
			assertTrue(report.contains("Object: obj-admin (owner: admin)"));
			assertTrue(report.contains("/object/2"));
			assertFalse(report.contains("/object/99"));
		} finally {
			server.stop(0);
		}
	}

	private static int count(String text, String fragment) {
		int occurrences = 0;
		for (int index = text.indexOf(fragment); index >= 0; index = text.indexOf(fragment, index + fragment.length())) {
			occurrences++;
		}
		return occurrences;
	}

	private static void writeResponse(
			com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
		byte[] response = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, response.length);
		try (var output = exchange.getResponseBody()) {
			output.write(response);
		}
	}
}
