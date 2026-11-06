package bes.tcc.apiauthtest.dto;

import java.util.List;

public record AuthenticationConfig(String url, List<AuthenticationUser> users) {
	public AuthenticationConfig {
		users = List.copyOf(users);
	}
}
