package uk.gov.pay.adminusers.model;
public class ServiceUpdateRequest {
    public static final java.lang.String FIELD_OP = "op";

    public static final java.lang.String FIELD_PATH = "path";

    public static final java.lang.String FIELD_VALUE = "value";

    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @io.swagger.v3.oas.annotations.media.Schema(example = "replace")
    private java.lang.String op;

    @io.swagger.v3.oas.annotations.media.Schema(example = "experimental_features_enabled")
    private java.lang.String path;

    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    private com.fasterxml.jackson.databind.JsonNode value;

    public java.lang.String getOp() {
        return op;
    }

    public java.lang.String getValue() {
        return value.asText();
    }

    public java.lang.String getPath() {
        return path;
    }

    public java.lang.String valueAsString() {
        if ((value != null) && value.isTextual()) {
            return value.asText();
        }
        return null;
    }

    public java.util.List<java.lang.String> valueAsList() {
        java.util.List<java.lang.String> values = new java.util.ArrayList<>();
        if ((value != null) && value.isArray()) {
            value.elements().forEachRemaining(node -> values.add(node.textValue()));
        }
        return values;
    }

    public java.util.Map<java.lang.String, java.lang.Object> valueAsObject() {
        if (value != null) {
            if ((value.isTextual() && (!org.apache.commons.lang3.StringUtils.isEmpty(value.asText()))) || value.isObject()) {
                try {
                    return uk.gov.pay.adminusers.model.ServiceUpdateRequest.objectMapper.readValue(value.traverse(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<java.lang.String, java.lang.Object>>() {});
                } catch (java.io.IOException e) {
                    throw new java.lang.RuntimeException("Malformed JSON object in ServiceUpdateRequest.value", e);
                }
            }
        }
        return null;
    }

    public boolean valueAsBoolean() {
        return (value != null) && java.lang.Boolean.parseBoolean(value.asText());
    }

    public java.time.ZonedDateTime valueAsDateTime() {
        if (value != null) {
            return java.time.ZonedDateTime.parse(value.asText());
        }
        return null;
    }

    private ServiceUpdateRequest(java.lang.String op, java.lang.String path, com.fasterxml.jackson.databind.JsonNode value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public static uk.gov.pay.adminusers.model.ServiceUpdateRequest from(com.fasterxml.jackson.databind.JsonNode payload) {
        return new uk.gov.pay.adminusers.model.ServiceUpdateRequest(payload.get(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_OP).asText(), payload.get(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_PATH).asText(), payload.get(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE));
    }

    public static java.util.List<uk.gov.pay.adminusers.model.ServiceUpdateRequest> getUpdateRequests(com.fasterxml.jackson.databind.JsonNode payload) {
        if (payload.isArray()) {
            java.util.List<uk.gov.pay.adminusers.model.ServiceUpdateRequest> operations = new java.util.ArrayList<>();
            payload.forEach(op -> operations.add(uk.gov.pay.adminusers.model.ServiceUpdateRequest.from(op)));
            return operations;
        } else {
            return java.util.Collections.singletonList(uk.gov.pay.adminusers.model.ServiceUpdateRequest.from(payload));
        }
    }
}
