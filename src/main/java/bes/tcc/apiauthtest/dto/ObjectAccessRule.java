package bes.tcc.apiauthtest.dto;

import java.util.List;
import java.util.Locale;

/**
 * Os objetos declarados para uma operação do OpenAPI (método + caminho).
 */
public record ObjectAccessRule(String method, String path, List<ObjectInstance> objects) {

	public ObjectAccessRule {
		method = method.toUpperCase(Locale.ROOT);
		objects = List.copyOf(objects);
	}

	public boolean matches(Endpoint endpoint) {
		return method.equalsIgnoreCase(endpoint.getMethod()) && path.equals(endpoint.getUrl());
	}

	public String operation() {
		return method + " " + path;
	}
}
