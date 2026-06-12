package uk.gov.pay.adminusers.resources;
public class InviteRequestValidator {
    private static final int MAX_LENGTH_CODE = 255;

    private final uk.gov.pay.adminusers.validations.RequestValidations requestValidations;

    @com.google.inject.Inject
    public InviteRequestValidator(uk.gov.pay.adminusers.validations.RequestValidations requestValidations) {
        this.requestValidations = requestValidations;
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateCreateUserRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SERVICE_EXTERNAL_ID, uk.gov.pay.adminusers.model.InviteRequest.FIELD_EMAIL, uk.gov.pay.adminusers.model.InviteRequest.FIELD_ROLE_NAME, uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SENDER);
        return missingMandatoryFields.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateGenerateOtpRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_TELEPHONE_NUMBER, uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_PASSWORD);
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        if (payload.get(uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_CODE) != null) {
            java.util.Optional<java.util.List<java.lang.String>> invalidLength = requestValidations.checkMaxLength(payload, uk.gov.pay.adminusers.resources.InviteRequestValidator.MAX_LENGTH_CODE, uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_CODE);
            return invalidLength.map(uk.gov.pay.adminusers.utils.Errors::from);
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateResendOtpRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_CODE, uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_TELEPHONE_NUMBER);
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        java.util.Optional<java.util.List<java.lang.String>> invalidLength = requestValidations.checkMaxLength(payload, uk.gov.pay.adminusers.resources.InviteRequestValidator.MAX_LENGTH_CODE, uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_CODE);
        return invalidLength.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateOtpValidationRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.InviteValidateOtpRequest.FIELD_CODE, uk.gov.pay.adminusers.model.InviteValidateOtpRequest.FIELD_OTP);
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        java.util.Optional<java.util.List<java.lang.String>> invalidLength = requestValidations.checkMaxLength(payload, uk.gov.pay.adminusers.resources.InviteRequestValidator.MAX_LENGTH_CODE, uk.gov.pay.adminusers.model.InviteValidateOtpRequest.FIELD_CODE);
        return invalidLength.map(uk.gov.pay.adminusers.utils.Errors::from);
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateCreateServiceRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFields = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.model.InviteServiceRequest.FIELD_EMAIL);
        if (missingMandatoryFields.isPresent()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFields.get()));
        }
        java.lang.String email = payload.get(uk.gov.pay.adminusers.model.InviteServiceRequest.FIELD_EMAIL).asText();
        if (!uk.gov.pay.adminusers.utils.email.EmailValidator.isValid(email)) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(java.lang.String.format("Field [%s] must be a valid email address", uk.gov.pay.adminusers.model.InviteServiceRequest.FIELD_EMAIL)));
        }
        if (!uk.gov.pay.adminusers.utils.email.EmailValidator.isPublicSectorEmail(email)) {
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.invalidPublicSectorEmail(email);
        }
        com.fasterxml.jackson.databind.JsonNode telephoneNumberJsonNode = payload.get(uk.gov.pay.adminusers.model.InviteServiceRequest.FIELD_TELEPHONE_NUMBER);
        if ((telephoneNumberJsonNode != null) && (!uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.isValidPhoneNumber(telephoneNumberJsonNode.asText()))) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(java.lang.String.format("Field [%s] must be a valid telephone number", uk.gov.pay.adminusers.model.InviteServiceRequest.FIELD_TELEPHONE_NUMBER)));
        }
        return java.util.Optional.empty();
    }
}
