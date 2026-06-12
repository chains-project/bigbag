package uk.gov.pay.adminusers.resources;
public class UserRequestValidator {
    private static final int MAX_LENGTH_FIELD_USERNAME = 255;

    private final uk.gov.pay.adminusers.validations.RequestValidations requestValidations;

    @com.google.inject.Inject
    public UserRequestValidator(uk.gov.pay.adminusers.validations.RequestValidations requestValidations) {
        this.requestValidations = requestValidations;
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateAuthenticateRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.User.FIELD_USERNAME, uk.gov.pay.adminusers.model.User.FIELD_PASSWORD);
        return missingMandatoryFields.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateCreateRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.User.FIELD_USERNAME, uk.gov.pay.adminusers.model.User.FIELD_EMAIL, uk.gov.pay.adminusers.model.User.FIELD_TELEPHONE_NUMBER, uk.gov.pay.adminusers.model.User.FIELD_ROLE_NAME);
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        java.util.Optional<java.util.List<java.lang.String>> invalidData = requestValidations.checkIsValidTelephoneNumber(payload, uk.gov.pay.adminusers.model.User.FIELD_TELEPHONE_NUMBER);
        if (invalidData.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(invalidData.get()));
        }
        java.util.Optional<java.util.List<java.lang.String>> invalidLength = requestValidations.checkMaxLength(payload, uk.gov.pay.adminusers.resources.UserRequestValidator.MAX_LENGTH_FIELD_USERNAME, uk.gov.pay.adminusers.model.User.FIELD_USERNAME);
        return invalidLength.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateNewSecondFactorPasscodeRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        if ((payload != null) && (payload.get("provisional") != null)) {
            return requestValidations.checkIsBoolean(payload, "provisional").map(uk.gov.pay.adminusers.utils.Errors::from);
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validate2FAAuthRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, "code");
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        java.util.Optional<java.util.List<java.lang.String>> notNumeric = requestValidations.checkIsNumeric(payload, "code");
        return notNumeric.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validate2faActivateRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, "code", "second_factor");
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        java.util.Optional<java.util.List<java.lang.String>> notNumeric = requestValidations.checkIsNumeric(payload, "code");
        if (notNumeric.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(notNumeric.get()));
        }
        java.lang.String secondFactor = payload.get("second_factor").asText();
        try {
            uk.gov.pay.adminusers.model.SecondFactorMethod.valueOf(secondFactor);
        } catch (java.lang.IllegalArgumentException e) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(java.util.List.of(java.lang.String.format("Invalid second_factor [%s]", secondFactor))));
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateServiceRole(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, "role_name");
        return missingMandatoryFields.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateAssignServiceRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.User.FIELD_SERVICE_EXTERNAL_ID, uk.gov.pay.adminusers.model.User.FIELD_ROLE_NAME);
        return missingMandatoryFields.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validatePatchRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, "op", "path", "value");
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        java.lang.String path = payload.get("path").asText();
        if (!uk.gov.pay.adminusers.validations.UserPatchValidations.isPathAllowed(path)) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(java.util.List.of(java.lang.String.format("Patching path [%s] not allowed", path))));
        }
        java.lang.String op = payload.get("op").asText();
        if (!uk.gov.pay.adminusers.validations.UserPatchValidations.isAllowedOpForPath(path, op)) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(java.util.List.of(java.lang.String.format("Operation [%s] not allowed for path [%s]", op, path))));
        }
        java.util.Optional<java.util.List<java.lang.String>> invalidData = checkValidPatchValue(payload.get("value"), uk.gov.pay.adminusers.validations.UserPatchValidations.getUserPatchPathValidations(path));
        return invalidData.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateFindRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.User.FIELD_USERNAME);
        return missingMandatoryFields.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    private java.util.Optional<java.util.List<java.lang.String>> checkValidPatchValue(com.fasterxml.jackson.databind.JsonNode valueNode, java.util.Collection<org.apache.commons.lang3.tuple.Pair<java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean>, java.lang.String>> pathValidations) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        pathValidations.forEach(validationPair -> {
            if (validationPair.getLeft().apply(valueNode)) {
                errors.add(validationPair.getRight());
            }
        });
        return errors.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(errors);
    }
}
