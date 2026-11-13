package uk.gov.pay.adminusers.resources;
class GovUkPayAgreementRequestValidatorTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator validator;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        validator = new uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator(new uk.gov.pay.adminusers.validations.RequestValidations());
    }

    @org.junit.jupiter.api.Test
    void shouldPassValidation() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("user_external_id", "abcde1234"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void shouldFailValidation_whenUserExternalIdIsEmpty() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("user_external_id", ""));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.get().getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.get().getErrors(), org.hamcrest.Matchers.hasItems("Field [user_external_id] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldFailValidation_whenUserExternalIdIsNotString() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("user_external_id", true));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.get().getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.get().getErrors(), org.hamcrest.Matchers.hasItems("Field [user_external_id] must be a valid user ID"));
    }

    @org.junit.jupiter.api.Test
    void shouldFailValidation_whenUserExternalIdIsMissing() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("luser_external_id", "abcde1234"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.get().getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.get().getErrors(), org.hamcrest.Matchers.hasItems("Field [user_external_id] is required"));
    }
}
