package uk.gov.pay.adminusers.resources;
public class ResetPasswordValidator {
    private final uk.gov.pay.adminusers.validations.RequestValidations requestValidations;

    @com.google.inject.Inject
    public ResetPasswordValidator(uk.gov.pay.adminusers.validations.RequestValidations requestValidations) {
        this.requestValidations = requestValidations;
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateResetRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        if (payload == null) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(java.util.List.of("invalid JSON")));
        }
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.resources.ResetPasswordResource.FIELD_CODE, uk.gov.pay.adminusers.resources.ResetPasswordResource.FIELD_PASSWORD);
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        java.util.Optional<java.util.List<java.lang.String>> invalidLength = checkLength(payload, uk.gov.pay.adminusers.resources.ResetPasswordResource.FIELD_CODE);
        return invalidLength.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    private java.util.Optional<java.util.List<java.lang.String>> checkLength(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return requestValidations.applyCheck(payload, exceedsMaxLength(), fieldNames, "Field [%s] must have a maximum length of 255 characters");
    }

    private java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> exceedsMaxLength() {
        return jsonNode -> jsonNode.asText().length() > 255;
    }
}
