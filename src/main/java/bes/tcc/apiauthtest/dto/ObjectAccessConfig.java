package bes.tcc.apiauthtest.dto;

import java.util.List;
import java.util.Optional;

/**
 * Configuração de BOLA: para cada operação, quais objetos existem e quem é o dono de cada um.
 */
public record ObjectAccessConfig(List<ObjectAccessRule> rules) {

	public ObjectAccessConfig {
		rules = rules == null ? List.of() : List.copyOf(rules);
	}

	public static ObjectAccessConfig empty() {
		return new ObjectAccessConfig(List.of());
	}

	public Optional<ObjectAccessRule> ruleFor(Endpoint endpoint) {
		return rules.stream().filter(rule -> rule.matches(endpoint)).findFirst();
	}
}
