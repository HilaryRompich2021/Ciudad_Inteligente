package com.ciudadesinteligentes.ingestor.config;

import com.ciudadesinteligentes.ingestor.util.CanonicalEventValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;

@Configuration
public class SchemaConfig {
    
    @Bean
    public String canonicalEventSchema() throws IOException {
        ClassPathResource resource = new ClassPathResource("canonical-event-schema.json");
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes());
        }
    }
    
    @Bean
    public CanonicalEventValidator canonicalEventValidator(String canonicalEventSchema) {
        return new CanonicalEventValidator(canonicalEventSchema);
    }
}
