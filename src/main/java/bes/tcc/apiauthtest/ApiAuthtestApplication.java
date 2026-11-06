package bes.tcc.apiauthtest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;

import bes.tcc.apiauthtest.dto.Endpoint;
import bes.tcc.apiauthtest.service.AuthenticationConfigService;
import bes.tcc.apiauthtest.service.EndpointExecutionService;
import bes.tcc.apiauthtest.service.EndpointService;

@SpringBootApplication
public class ApiAuthtestApplication implements CommandLineRunner {

	private final EndpointService endpointService;
	private final AuthenticationConfigService authenticationConfigService;
	private final EndpointExecutionService endpointExecutionService;
	private final Resource openApiSpec;
	private final String baseUrl;
	private final Path reportPath;

	public ApiAuthtestApplication(
			EndpointService endpointService,
			AuthenticationConfigService authenticationConfigService,
			EndpointExecutionService endpointExecutionService,
			@Value("${AUTHTEST_SPEC:classpath:openapi.yaml}") Resource openApiSpec,
			@Value("${AUTHTEST_BASE_URL:http://localhost:8091}") String baseUrl,
			@Value("${AUTHTEST_REPORT_PATH:authtest-report.txt}") Path reportPath) {
		this.endpointService = endpointService;
		this.authenticationConfigService = authenticationConfigService;
		this.endpointExecutionService = endpointExecutionService;
		this.openApiSpec = openApiSpec;
		this.baseUrl = baseUrl;
		this.reportPath = reportPath;
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
		Path generatedReport = endpointExecutionService.execute(
				authenticationConfigService.read(), endpoints, baseUrl, pathParameters, reportPath);
		System.out.println("Report written to " + generatedReport);
	}

}
