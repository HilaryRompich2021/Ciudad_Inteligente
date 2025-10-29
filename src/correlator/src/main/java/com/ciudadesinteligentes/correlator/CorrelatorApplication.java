package com.ciudadesinteligentes.correlator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.ciudadesinteligentes.correlator")
@EnableJpaRepositories(basePackages = "com.ciudadesinteligentes.correlator.repository")
@EntityScan(basePackages = "com.ciudadesinteligentes.correlator.model")
public class CorrelatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CorrelatorApplication.class, args);
	}

}
