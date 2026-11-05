package bes.tcc.apiauthtest.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

import bes.tcc.apiauthtest.dto.Endpoint;

@Service 
public class EndpointService {
    
    private static final List<String> httpMethods = Arrays.asList("get", "post", "put", "delete", "patch", "options", "head", "trace");

    public List<Endpoint> readEndpoints(Resource openApiSpec) throws IOException {
		
		Map<?, ?> pathsDocument = getMapFromDoc(openApiSpec);
		List<Endpoint> endpoints = new ArrayList<>();
		
		for (Map.Entry<?, ?> pathEntry : (pathsDocument).entrySet()) {

			if (!(pathEntry.getKey() instanceof String) || !(pathEntry.getValue() instanceof Map)) {
				continue;
			}

			String path = (String) pathEntry.getKey();

			for (Map.Entry<?, ?> operationEntry : ((Map<?, ?>) pathEntry.getValue()).entrySet()) {

				String method = String.valueOf(operationEntry.getKey()).toLowerCase();
				
				if (httpMethods.contains(method) && operationEntry.getValue() instanceof Map) {

					Map<?, ?> operation = (Map<?, ?>) operationEntry.getValue();
					endpoints.add(new Endpoint(path, method.toUpperCase(),
							asString(operation.get("operationId")), asString(operation.get("summary"))));
				
				}

			}

		}

		return endpoints;

	}

	private String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private Map<?, ?> getMapFromDoc(Resource openApiSpec) throws IOException{

		Object yamlDocument;
		try (InputStream input = openApiSpec.getInputStream()) {
			yamlDocument = new Yaml().load(input);
		}

		if (!(yamlDocument instanceof Map)) {
			return new HashMap<>();
		}

		Object pathsDocument = ((Map<?, ?>) yamlDocument).get("paths");
		if (!(pathsDocument instanceof Map)) {
			return new HashMap<>();
		}

		return (Map<?, ?>) pathsDocument;
		
	}

}
