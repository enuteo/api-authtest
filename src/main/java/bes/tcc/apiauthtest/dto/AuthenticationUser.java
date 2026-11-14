package bes.tcc.apiauthtest.dto;

import java.util.List;

public record AuthenticationUser(String username, String password, List<String> roles, List<Long> objects) {
	public AuthenticationUser(String username, String password, List<String> roles) {
		this(username, password, roles, List.of());
	}

	public AuthenticationUser {
		roles = roles == null ? List.of() : List.copyOf(roles);
		objects = objects == null ? List.of() : List.copyOf(objects);
	}
}
