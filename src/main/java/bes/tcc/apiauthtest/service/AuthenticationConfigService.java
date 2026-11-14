package bes.tcc.apiauthtest.service;

import bes.tcc.apiauthtest.dto.AuthenticationConfig;
import bes.tcc.apiauthtest.dto.AuthenticationUser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Service
public class AuthenticationConfigService {
	private final Resource authenticationSpec;
	private final Environment environment;

	public AuthenticationConfigService(
			@org.springframework.beans.factory.annotation.Value(
					"${AUTHTEST_USERS_SPEC:classpath:users.yaml}") Resource authenticationSpec,
			Environment environment) {
		this.authenticationSpec = authenticationSpec;
		this.environment = environment;
	}

	public AuthenticationConfig read() throws IOException {
		Object document;
		try (InputStream input = authenticationSpec.getInputStream()) {
			document = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
		}

		if (!(document instanceof Map<?, ?> root) || !(root.get("authentication") instanceof Map<?, ?> auth)) {
			throw new IllegalArgumentException("Users YAML must contain an 'authentication' mapping.");
		}

		String url = resolve(auth, "url");
		validateUrl(url);
		if (!(auth.get("users") instanceof List<?> userEntries) || userEntries.isEmpty()) {
			throw new IllegalArgumentException("Users YAML must define at least one authentication user.");
		}

		List<AuthenticationUser> users = new ArrayList<>();
		HashSet<String> usernames = new HashSet<>();
		for (Object entry : userEntries) {
			if (!(entry instanceof Map<?, ?> user)) {
				throw new IllegalArgumentException("Each entry in authentication.users must be a mapping.");
			}
			String username = resolve(user, "username");
			String password = resolve(user, "password");
			List<String> roles = readRoles(user.get("roles"), username);
			List<Long> objects = readObjects(user.get("objects"), username);
			if (!usernames.add(username)) {
				throw new IllegalArgumentException("Duplicate authentication username: " + username);
			}
			users.add(new AuthenticationUser(username, password, roles, objects));
		}
		return new AuthenticationConfig(url, users);
	}

	private String resolve(Map<?, ?> auth, String key) {
		Object value = auth.get(key);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException("Authentication YAML field '" + key + "' must be a non-empty string.");
		}
		String resolved = environment.resolveRequiredPlaceholders(text);
		if (resolved.isBlank() || resolved.contains("${")) {
			throw new IllegalArgumentException("Authentication YAML field '" + key + "' has an unresolved value.");
		}
		return resolved;
	}

	private List<String> readRoles(Object value, String username) {
		if (!(value instanceof List<?> entries)) {
			throw new IllegalArgumentException("Roles for user '" + username + "' must be a list.");
		}
		List<String> roles = new ArrayList<>();
		for (Object entry : entries) {
			if (!(entry instanceof String role) || role.isBlank()) {
				throw new IllegalArgumentException("Roles for user '" + username + "' must be non-empty strings.");
			}
			roles.add(role);
		}
		return List.copyOf(roles);
	}

	private List<Long> readObjects(Object value, String username) {
		if (!(value instanceof List<?> entries)) {
			throw new IllegalArgumentException("Objects for user '" + username + "' must be a list.");
		}
		List<Long> objects = new ArrayList<>();
		for (Object entry : entries) {
			if (!(entry instanceof Number number)) {
				throw new IllegalArgumentException("Objects for user '" + username + "' must be positive integers.");
			}
			long id;
			try {
				id = Long.parseLong(number.toString());
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException(
						"Objects for user '" + username + "' must be positive integers.", exception);
			}
			if (id <= 0) {
				throw new IllegalArgumentException("Objects for user '" + username + "' must be positive integers.");
			}
			objects.add(id);
		}
		return List.copyOf(objects);
	}

	private void validateUrl(String url) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Authentication URL must be a valid absolute HTTP(S) URL.", exception);
		}
		if (!uri.isAbsolute()
				|| !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
				|| uri.getHost() == null) {
			throw new IllegalArgumentException("Authentication URL must be a valid absolute HTTP(S) URL.");
		}
	}
}
