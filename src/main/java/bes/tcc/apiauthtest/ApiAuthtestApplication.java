package bes.tcc.apiauthtest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;

import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.service.CallService;
import bes.tcc.apiauthtest.service.EndpointService;

@SpringBootApplication
public class ApiAuthtestApplication implements CommandLineRunner {

	private final EndpointService endpointService;
	private final CallService callService;
	private final Resource openApiSpec;
	private final String baseUrl;

	public ApiAuthtestApplication(
			EndpointService endpointService,
			CallService callService,
			@Value("${AUTHTEST_SPEC:classpath:openapi.yaml}") Resource openApiSpec,
			@Value("${AUTHTEST_BASE_URL:http://localhost:8080}") String baseUrl) {
		this.endpointService = endpointService;
		this.callService = callService;
		this.openApiSpec = openApiSpec;
		this.baseUrl = baseUrl;
	}

	public static void main(String[] args) {
		SpringApplication.run(ApiAuthtestApplication.class, args);
	}

	Map<String, String> pathParameters = Map.of(
		"id", "1",
		"param2", "value2"
	);

	@Override
	public void run(String... args) throws Exception {

		List<Endpoint> endpoints = endpointService.readEndpoints(openApiSpec);

		for (Endpoint endpoint : endpoints) {
			callService.call(
				endpoint, 
				baseUrl,
				pathParameters, 
				null, 
				null, 
				null
			);
		}
		// endpoints.forEach(e -> System.out.println(e.getMethod() + " " + e.getUrl() + " - " + e.getOperationId() + " - " + e.getSummary()));
	
	}

}
