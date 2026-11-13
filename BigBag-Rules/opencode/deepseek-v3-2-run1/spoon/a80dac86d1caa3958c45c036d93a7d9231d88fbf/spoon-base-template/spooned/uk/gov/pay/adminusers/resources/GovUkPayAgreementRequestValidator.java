package uk.gov.pay.adminusers.resources;
public class GovUkPayAgreementRequestValidator {
    private static final java.lang.String FIELD_USER_ID = "user_external_id";

    private final uk.gov.pay.adminusers.validations.RequestValidations requestValidations;

    @com.google.inject.Inject
    public GovUkPayAgreementRequestValidator(uk.gov.pay.adminusers.validations.RequestValidations requestValidations) {
        this.requestValidations = requestValidations;
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateCreateRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        return requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator.FIELD_USER_ID).map(strings -> java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(strings))).orElseGet(() -> requestValidations.checkIsString("Field [%s] must be a valid user ID", payload, uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator.FIELD_USER_ID).map(uk.gov.pay.adminusers.utils.Errors::from));
    }
}
