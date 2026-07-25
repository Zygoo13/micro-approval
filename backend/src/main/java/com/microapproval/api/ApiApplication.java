package com.microapproval.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.microapproval.api.config.AiAnalysisProperties;
import com.microapproval.api.config.AiCredentialsProperties;

@SpringBootApplication
@EnableConfigurationProperties({AiAnalysisProperties.class, AiCredentialsProperties.class})
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
