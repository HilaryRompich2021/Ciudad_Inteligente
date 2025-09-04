package com.ciudadesinteligentes.ingestor.util;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

public class CanonicalEventValidator {
    private final JsonSchema schema;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CanonicalEventValidator(String schemaJson) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        this.schema = factory.getSchema(schemaJson);
    }

    public void validate(String eventJson) throws Exception {
        JsonNode eventNode = objectMapper.readTree(eventJson);
        Set<ValidationMessage> errors = schema.validate(eventNode);
        if (!errors.isEmpty()) {
            throw new RuntimeException("Validation errors: " + errors.toString());
        }
    }
}
