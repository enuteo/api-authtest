package bes.tcc.apiauthtest.service;

import bes.tcc.apiauthtest.dto.AuthenticationConfig;
import bes.tcc.apiauthtest.dto.AuthenticationUser;
import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.dto.ObjectAccessConfig;
import bes.tcc.apiauthtest.dto.ObjectAccessRule;
import bes.tcc.apiauthtest.dto.ObjectInstance;
import bes.tcc.apiauthtest.util.RequestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Lê a configuração de BOLA (objects.yaml): para cada operação, os objetos usados no teste,
 * os parâmetros que identificam cada objeto e o usuário dono. Se o arquivo não existir,
 * nenhuma operação tem dono declarado e a execução segue como antes.
 */
@Service
public class ObjectAccessConfigService {
	private final Resource objectsSpec;
	private final Environment environment;

	public ObjectAccessConfigService(
			@org.springframework.beans.factory.annotation.Value(
					"${AUTHTEST_OBJECTS_SPEC:classpath:objects.yaml}") Resource objectsSpec,
			Environment environment) {
		this.objectsSpec = objectsSpec;
		this.environment = environment;
	}

	public ObjectAccessConfig read(AuthenticationConfig authentication, List<Endpoint> endpoints) throws IOException {
		if (objectsSpec == null || !objectsSpec.exists()) {
			return ObjectAccessConfig.empty();
		}

		Object document;
		try (InputStream input = objectsSpec.getInputStream()) {
			document = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
		}
		if (document == null) {
			return ObjectAccessConfig.empty();
		}
		if (!(document instanceof Map<?, ?> root) || !(root.get("objects") instanceof List<?> ruleEntries)) {
			throw new IllegalArgumentException("Objects YAML must contain an 'objects' list.");
		}

		Set<String> usernames = new HashSet<>();
		for (AuthenticationUser user : authentication.users()) {
			usernames.add(user.username());
		}

		List<ObjectAccessRule> rules = new ArrayList<>();
		Set<String> operations = new HashSet<>();
		for (Object entry : ruleEntries) {
			if (!(entry instanceof Map<?, ?> ruleEntry)) {
				throw new IllegalArgumentException("Each entry in objects must be a mapping.");
			}
			ObjectAccessRule rule = readRule(ruleEntry, usernames, endpoints);
			if (!operations.add(rule.operation())) {
				throw new IllegalArgumentException("Duplicate operation in objects YAML: " + rule.operation());
			}
			rules.add(rule);
		}
		return new ObjectAccessConfig(rules);
	}

	private ObjectAccessRule readRule(Map<?, ?> ruleEntry, Set<String> usernames, List<Endpoint> endpoints) {
		String operation = requiredString(ruleEntry, "operation", "objects");
		String[] parts = operation.trim().split("\\s+", 2);
		if (parts.length != 2 || !parts[1].startsWith("/")) {
			throw new IllegalArgumentException(
					"Operation '" + operation + "' must be in the format 'METHOD /path', e.g. 'GET /object/{id}'.");
		}
		String method = parts[0].toUpperCase(Locale.ROOT);
		String path = parts[1];

		Endpoint endpoint = endpoints.stream()
				.filter(candidate -> candidate.getMethod().equalsIgnoreCase(method) && candidate.getUrl().equals(path))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Operation '" + method + " " + path + "' is not declared in the OpenAPI specification."));

		if (!(ruleEntry.get("instances") instanceof List<?> instanceEntries) || instanceEntries.isEmpty()) {
			throw new IllegalArgumentException(
					"Operation '" + method + " " + path + "' must declare at least one object in 'instances'.");
		}

		Set<String> requiredPathParameters = pathParameterNames(endpoint.getUrl());
		List<ObjectInstance> objects = new ArrayList<>();
		Set<String> labels = new HashSet<>();
		int index = 0;
		for (Object instanceEntry : instanceEntries) {
			index++;
			String context = method + " " + path + " (object #" + index + ")";
			if (!(instanceEntry instanceof Map<?, ?> instance)) {
				throw new IllegalArgumentException("Each object of " + method + " " + path + " must be a mapping.");
			}
			ObjectInstance object = readInstance(instance, context, index, usernames, requiredPathParameters);
			if (!labels.add(object.label())) {
				throw new IllegalArgumentException(
						"Duplicate object id '" + object.label() + "' in " + method + " " + path + ".");
			}
			objects.add(object);
		}
		return new ObjectAccessRule(method, path, objects);
	}

	private ObjectInstance readInstance(
			Map<?, ?> instance,
			String context,
			int index,
			Set<String> usernames,
			Set<String> requiredPathParameters) {
		String owner = resolve(requiredString(instance, "owner", context), "owner", context);
		requireKnownUser(owner, context, usernames);

		List<String> sharedWith = new ArrayList<>();
		Object sharedEntries = instance.get("sharedWith");
		if (sharedEntries != null) {
			if (!(sharedEntries instanceof List<?> entries)) {
				throw new IllegalArgumentException("Field 'sharedWith' of " + context + " must be a list of usernames.");
			}
			for (Object entry : entries) {
				if (!(entry instanceof String text) || text.isBlank()) {
					throw new IllegalArgumentException("Field 'sharedWith' of " + context + " must contain usernames.");
				}
				String username = resolve(text, "sharedWith", context);
				requireKnownUser(username, context, usernames);
				if (!username.equals(owner) && !sharedWith.contains(username)) {
					sharedWith.add(username);
				}
			}
		}

		Map<String, String> pathParameters = readParameters(instance.get("pathParameters"), "pathParameters", context);
		Map<String, String> queryParameters = readParameters(instance.get("queryParameters"), "queryParameters", context);
		if (!pathParameters.keySet().equals(requiredPathParameters)) {
			throw new IllegalArgumentException("Field 'pathParameters' of " + context
					+ " must define exactly the path parameters " + requiredPathParameters
					+ ", but defines " + pathParameters.keySet() + ".");
		}
		if (pathParameters.isEmpty() && queryParameters.isEmpty()) {
			throw new IllegalArgumentException(context
					+ " must identify the object with at least one path or query parameter.");
		}

		Object id = instance.get("id");
		String label = id == null ? "object-" + index : String.valueOf(id);
		if (label.isBlank()) {
			throw new IllegalArgumentException("Field 'id' of " + context + " must not be blank.");
		}
		return new ObjectInstance(label, pathParameters, queryParameters, owner, sharedWith);
	}

	private Map<String, String> readParameters(Object value, String field, String context) {
		if (value == null) {
			return Map.of();
		}
		if (!(value instanceof Map<?, ?> entries)) {
			throw new IllegalArgumentException("Field '" + field + "' of " + context + " must be a mapping.");
		}
		Map<String, String> parameters = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : entries.entrySet()) {
			if (!(entry.getKey() instanceof String name) || name.isBlank() || entry.getValue() == null
					|| entry.getValue() instanceof Map<?, ?> || entry.getValue() instanceof List<?>) {
				throw new IllegalArgumentException(
						"Field '" + field + "' of " + context + " must map parameter names to single values.");
			}
			parameters.put(name, resolve(String.valueOf(entry.getValue()), field, context));
		}
		return parameters;
	}

	private Set<String> pathParameterNames(String path) {
		Set<String> names = new LinkedHashSet<>();
		Matcher matcher = RequestUtils.PATH_PARAMETER.matcher(path);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}

	private void requireKnownUser(String username, String context, Set<String> usernames) {
		if (!usernames.contains(username)) {
			throw new IllegalArgumentException("User '" + username + "' in " + context
					+ " is not declared in the users YAML.");
		}
	}

	private String requiredString(Map<?, ?> entry, String key, String context) {
		Object value = entry.get(key);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException("Field '" + key + "' of " + context + " must be a non-empty string.");
		}
		return text;
	}

	private String resolve(String text, String key, String context) {
		String resolved = environment.resolveRequiredPlaceholders(text);
		if (resolved.isBlank() || resolved.contains("${")) {
			throw new IllegalArgumentException("Field '" + key + "' of " + context + " has an unresolved value.");
		}
		return resolved;
	}
}
