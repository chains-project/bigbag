package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PatchRequest {
    public static final java.lang.String PATH_SESSION_VERSION = "sessionVersion";

    public static final java.lang.String PATH_DISABLED = "disabled";

    public static final java.lang.String PATH_TELEPHONE_NUMBER = "telephone_number";

    public static final java.lang.String PATH_EMAIL = "email";

    public static final java.lang.String PATH_FEATURES = "features";

    @io.swagger.v3.oas.annotations.media.Schema(example = "replace")
    private java.lang.String op;

    @io.swagger.v3.oas.annotations.media.Schema(example = "email")
    private java.lang.String path;

    @io.swagger.v3.oas.annotations.media.Schema(example = "user@somegovernmentdept.gov.uk")
    private java.lang.String value;

    private PatchRequest(java.lang.String op, java.lang.String path, java.lang.String value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public static uk.gov.pay.adminusers.model.PatchRequest from(com.fasterxml.jackson.databind.JsonNode node) {
        return new uk.gov.pay.adminusers.model.PatchRequest(node.get("op").asText(), node.get("path").asText(), node.get("value").asText());
    }

    public java.lang.String getOp() {
        return op;
    }

    public java.lang.String getPath() {
        return path;
    }

    public java.lang.String getValue() {
        return value;
    }
}
