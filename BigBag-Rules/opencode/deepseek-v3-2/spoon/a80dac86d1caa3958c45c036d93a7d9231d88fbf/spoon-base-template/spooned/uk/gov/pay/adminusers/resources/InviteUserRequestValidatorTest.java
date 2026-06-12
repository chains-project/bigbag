package uk.gov.pay.adminusers.resources;
class InviteUserRequestValidatorTest {
    private uk.gov.pay.adminusers.resources.InviteRequestValidator validator;

    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void before() {
        validator = new uk.gov.pay.adminusers.resources.InviteRequestValidator(new uk.gov.pay.adminusers.validations.RequestValidations());
    }

    @org.junit.jupiter.api.Test
    void validateCreateUserRequest_shouldError_ifAllMandatoryFieldsAreMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = "{}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateUserRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(4));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [service_external_id] is required", "Field [sender] is required", "Field [email] is required", "Field [role_name] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateCreateUserRequest_shouldError_ifServiceExternalIdFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ((("{" + "\"sender\": \"12345abc\",") + "\"email\": \"email@example.com\",") + "\"role_name\": \"admin\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateUserRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [service_external_id] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateCreateUserRequest_shouldError_ifRoleNameFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ((("{" + "\"service_external_id\": \"service123\",") + "\"sender\": \"12345abc\",") + "\"email\": \"email@example.com\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateUserRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [role_name] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateCreateUserRequest_shouldError_ifEmailFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ((("{" + "\"service_external_id\": \"service123\",") + "\"sender\": \"12345abc\",") + "\"role_name\": \"admin\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateUserRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [email] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateCreateUserRequest_shouldError_ifSenderFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ((("{" + "\"service_external_id\": \"service123\",") + "\"email\": \"email@example.com\",") + "\"role_name\": \"admin\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateUserRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [sender] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateGenerateOtpRequest_shouldError_ifAllMandatoryFieldsAreMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = "{}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateGenerateOtpRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [telephone_number] is required", "Field [password] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateGenerateOtpRequest_shouldError_ifCodeFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ("{" + "\"password\": \"a-password\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateGenerateOtpRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [telephone_number] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateGenerateOtpRequest_shouldError_ifTelephoneNumberFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = (("{" + "\"code\": \"a-code\",") + "\"password\": \"a-password\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateGenerateOtpRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [telephone_number] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateGenerateOtpRequest_shouldError_ifPasswordFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = (("{" + "\"code\": \"a-code\",") + "\"telephone_number\": \"a-telephone_number\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateGenerateOtpRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [password] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateResendOtpRequest_shouldError_ifAllMandatoryFieldsAreMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = "{}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateResendOtpRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [code] is required", "Field [telephone_number] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateResendOtpRequest_shouldError_ifCodeFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ("{" + "\"telephone_number\": \"a-telephone_number\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateResendOtpRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [code] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateResendOtpRequest_shouldError_ifTelephoneNumberFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ("{" + "\"code\": \"a-code\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateResendOtpRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [telephone_number] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateOtpValidationRequest_shouldError_ifAllMandatoryFieldsAreMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = "{}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateOtpValidationRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [code] is required", "Field [otp] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateOtpValidationRequest_shouldError_ifCodeFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ("{" + "\"otp\": \"an-otp-code\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateOtpValidationRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [code] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateOtpValidationRequest_shouldError_ifOtpFieldIsMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ("{" + "\"code\": \"a-code\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateOtpValidationRequest(jsonNode);
        org.junit.jupiter.api.Assertions.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [otp] is required"));
    }

    @org.junit.jupiter.api.Test
    void validateCreateServiceRequest_shouldSuccess_ifAllFieldsArePresentAndValidEmailDomain() {
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("email", "example@example.gov.uk", "telephone_number", "01134960000", "password", "super-secure-password");
        com.fasterxml.jackson.databind.JsonNode payloadNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.valueToTree(payload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = validator.validateCreateServiceRequest(payloadNode);
        org.junit.jupiter.api.Assertions.assertFalse(errors.isPresent());
    }

    @org.junit.jupiter.api.Test
    void validateCreateServiceRequest_shouldSuccess_ifOnlyEmailFieldIsPresentAndValidEmailDomain() {
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("email", "example@example.gov.uk");
        com.fasterxml.jackson.databind.JsonNode payloadNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.valueToTree(payload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = validator.validateCreateServiceRequest(payloadNode);
        org.junit.jupiter.api.Assertions.assertFalse(errors.isPresent());
    }

    @org.junit.jupiter.api.Test
    void validateCreateServiceRequest_shouldFail_ifMissingRequiredField() {
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("telephone_number", "01134960000", "password", "super-secure-password");
        com.fasterxml.jackson.databind.JsonNode payloadNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.valueToTree(payload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = validator.validateCreateServiceRequest(payloadNode);
        org.junit.jupiter.api.Assertions.assertTrue(errors.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors(), org.hamcrest.Matchers.hasItems("Field [email] is required"));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().size(), org.hamcrest.core.Is.is(1));
    }

    @org.junit.jupiter.api.Test
    void validateCreateServiceRequest_shouldFail_ifInvalidEmailFormat() {
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("email", "exampleatexample.com", "telephone_number", "01134960000", "password", "super-secure-password");
        com.fasterxml.jackson.databind.JsonNode payloadNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.valueToTree(payload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = validator.validateCreateServiceRequest(payloadNode);
        org.junit.jupiter.api.Assertions.assertTrue(errors.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors(), org.hamcrest.Matchers.hasItems("Field [email] must be a valid email address"));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().size(), org.hamcrest.core.Is.is(1));
    }

    @org.junit.jupiter.api.Test
    void validateCreateServiceRequest_shouldFail_ifEmailAddressNotPublicSector() {
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("email", "example@example.com", "telephone_number", "01134960000", "password", "super-secure-password");
        com.fasterxml.jackson.databind.JsonNode payloadNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.valueToTree(payload);
        org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> validator.validateCreateServiceRequest(payloadNode));
    }

    @org.junit.jupiter.api.Test
    void validateCreateServiceRequest_shouldFail_ifTelephoneNumberIsInvalid() {
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("email", "example@example.gov.uk", "telephone_number", "0770090000A", "password", "super-secure-password");
        com.fasterxml.jackson.databind.JsonNode payloadNode = uk.gov.pay.adminusers.resources.InviteUserRequestValidatorTest.objectMapper.valueToTree(payload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = validator.validateCreateServiceRequest(payloadNode);
        org.junit.jupiter.api.Assertions.assertTrue(errors.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors(), org.hamcrest.Matchers.hasItems("Field [telephone_number] must be a valid telephone number"));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().size(), org.hamcrest.core.Is.is(1));
    }
}
