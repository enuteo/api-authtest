package bes.tcc.apiauthtest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.service.CallService;
import bes.tcc.apiauthtest.service.EndpointService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor 
@SpringBootApplication
public class ApiAuthtestApplication implements CommandLineRunner {

	private static final String path = "src/main/resources/openapi.yaml";

	private final EndpointService endpointService;
	private final CallService callService;

	public static void main(String[] args) {
		SpringApplication.run(ApiAuthtestApplication.class, args);
	}

	Map<String, String> pathParameters = Map.of(
		"id", "1",
		"param2", "value2"
	);

	@Override
	public void run(String... args) throws Exception {

		List<Endpoint> endpoints = endpointService.readEndpoints(path);

		for (Endpoint endpoint : endpoints) {
			callService.call(
				endpoint, 
				"http://localhost:8080", 
				pathParameters, 
				null, 
				null, 
				null
			);
		}
		// endpoints.forEach(e -> System.out.println(e.getMethod() + " " + e.getUrl() + " - " + e.getOperationId() + " - " + e.getSummary()));
	
	}

}
