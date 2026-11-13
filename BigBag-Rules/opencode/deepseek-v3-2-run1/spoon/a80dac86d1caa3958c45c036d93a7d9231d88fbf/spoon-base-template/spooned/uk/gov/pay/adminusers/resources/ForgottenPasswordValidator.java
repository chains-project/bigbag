package uk.gov.pay.adminusers.resources;
public class ForgottenPasswordValidator {
    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateCreateRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        if (((payload != null) && (payload.get("username") != null)) && (!org.apache.commons.lang3.StringUtils.isBlank(payload.get("username").asText()))) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(java.util.List.of("Field [username] is required")));
    }
}
