package bes.tcc.apiauthtest.dto;

import java.util.List;

public record AuthenticationUser(String username, String password, List<String> roles) {
	public AuthenticationUser {
		roles = roles == null ? List.of() : List.copyOf(roles);
	}
}
