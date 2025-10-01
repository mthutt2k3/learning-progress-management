package com.learning.progress;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@EnableJpaRepositories(basePackages = {"com.learning.progress.repository"})
@Slf4j
public class LearningProgressApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(LearningProgressApplication.class);
		Environment env = app.run(args).getEnvironment();
		logApplicationStartup(env);
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	private static void logApplicationStartup(Environment env) {
		String protocol = env.getProperty("server.ssl.key-store") != null ? "https" : "http";
		String serverPort = env.getProperty("server.port", "8080");
		String contextPath = env.getProperty("server.servlet.context-path", "/");

		String serverIp = "localhost";
		try {
			serverIp = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			log.warn("Could not determine server IP", e);
		}

		log.info("\n----------------------------------------------------------\n\t" +
						"Application '{}' is running! Access URLs:\n\t" +
						"Local: \t\t{}://localhost:{}{}\n\t" +
						"External: \t{}://{}:{}{}\n\t" +
						"Swagger UI: \t{}://{}:{}{}/swagger-ui.html\n\t" +
						"Profile(s): \t{}\n----------------------------------------------------------",
				env.getProperty("spring.application.name", "learning-progress-management"),
				protocol,
				serverPort,
				contextPath,
				protocol,
				serverIp,
				serverPort,
				contextPath,
				protocol,
				serverIp,
				serverPort,
				contextPath,
				env.getActiveProfiles());
	}
}
