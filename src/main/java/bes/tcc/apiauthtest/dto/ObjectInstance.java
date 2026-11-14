package bes.tcc.apiauthtest.dto;

import java.util.List;
import java.util.Map;

/**
 * Um objeto usado nos testes de BOLA: os parâmetros que o identificam na requisição,
 * o usuário dono e, opcionalmente, outros usuários que também podem acessá-lo.
 */
public record ObjectInstance(
		String label,
		Map<String, String> pathParameters,
		Map<String, String> queryParameters,
		String owner,
		List<String> sharedWith) {

	public ObjectInstance {
		pathParameters = pathParameters == null ? Map.of() : Map.copyOf(pathParameters);
		queryParameters = queryParameters == null ? Map.of() : Map.copyOf(queryParameters);
		sharedWith = sharedWith == null ? List.of() : List.copyOf(sharedWith);
	}

	public boolean isOwner(String username) {
		return owner.equals(username);
	}

	public boolean hasAccess(String username) {
		return isOwner(username) || sharedWith.contains(username);
	}
}
