package uk.gov.pay.adminusers.resources;
class UserRequestValidatorTest {
    private uk.gov.pay.adminusers.resources.UserRequestValidator validator;

    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void before() {
        validator = new uk.gov.pay.adminusers.resources.UserRequestValidator(new uk.gov.pay.adminusers.validations.RequestValidations());
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifAllMandatoryFieldsAreMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = "{}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(jsonNode);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(4));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [username] is required", "Field [email] is required", "Field [telephone_number] is required", "Field [role_name] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifSomeMandatoryFieldsAreMissing() throws java.lang.Exception {
        java.lang.String invalidPayload = ((("{" + "\"username\": \"a-username\",") + "\"email\": \"email@example.com\",") + "\"otp_key\": \"12345\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(jsonNode);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [role_name] is required", "Field [telephone_number] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifMandatoryPatchFieldsAreMissing() {
        com.fasterxml.jackson.databind.JsonNode invalidPayload = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        mockValidValuesFor(invalidPayload, java.util.Map.of("foo", "blah", "bar", "blah@blah.com"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(invalidPayload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(3));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [op] is required", "Field [path] is required", "Field [value] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifPathNotAllowed_whenPatching() {
        com.fasterxml.jackson.databind.JsonNode invalidPayload = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        mockValidValuesFor(invalidPayload, java.util.Map.of("op", "append", "path", "version", "value", "1"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(invalidPayload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Patching path [version] not allowed"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifPathOperationNotValid_whenPatching() {
        com.fasterxml.jackson.databind.JsonNode invalidPayload = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        mockValidValuesFor(invalidPayload, java.util.Map.of("op", "replace", "path", "sessionVersion", "value", "1"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(invalidPayload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Operation [replace] not allowed for path [sessionVersion]"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifSessionVersionNotNumeric_whenPatching() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("op", "append", "path", "sessionVersion", "value", "1r"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(payload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("path [sessionVersion] must contain a value of positive integer"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifDisabledNotBoolean_whenPatching() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("op", "replace", "path", "disabled", "value", "1r"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(payload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("path [disabled] must be contain value [true | false]"));
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_forDisabled_whenPatching() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("op", "replace", "path", "disabled", "value", "true"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(payload);
        junit.framework.TestCase.assertFalse(optionalErrors.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_forSessionVersion_whenPatching() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("op", "append", "path", "sessionVersion", "value", "2"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(payload);
        junit.framework.TestCase.assertFalse(optionalErrors.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_replacingTelephoneNumber_whenPatchingLocalTelephoneNumber() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("op", "replace", "path", "telephone_number", "value", "01134960000"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(payload);
        junit.framework.TestCase.assertFalse(optionalErrors.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_replacingTelephoneNumber_whenPatchingInternationalTelephoneNumber() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("op", "replace", "path", "telephone_number", "value", "+441134960000"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(payload);
        junit.framework.TestCase.assertFalse(optionalErrors.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldError_replacingTelephoneNumber_whenPatchingInvalidTelephoneNumber() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("op", "replace", "path", "telephone_number", "value", "(╯°□°）╯︵ ┻━┻"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(payload);
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("path [telephone_number] must contain a valid telephone number"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_whenPatchingInvalidEmail() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("op", "replace", "path", "email", "value", "(╯°□°）╯︵ ┻━┻"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validatePatchRequest(payload);
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("path [email] must contain a valid email"));
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_whenAddingServiceRole() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("service_external_id", "blah-blah", "role_name", "blah"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateAssignServiceRequest(payload);
        junit.framework.TestCase.assertFalse(optionalErrors.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldError_whenAddingServiceRole_ifRequiredParamMissing() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("service_external_id", "blah-blah"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateAssignServiceRequest(payload);
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [role_name] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifTelephoneNumberFieldIsInvalid() throws java.lang.Exception {
        java.lang.String invalidPayload = ((((((("{" + "\"username\": \"a-username\",") + "\"password\": \"a-password\",") + "\"email\": \"email@example.com\",") + "\"gateway_account_ids\": [\"1\"],") + "\"telephone_number\": \"(╯°□°）╯︵ ┻━┻\",") + "\"otp_key\": \"12345\",") + "\"role_name\": \"a-role\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(jsonNode);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [telephone_number] must be a valid telephone number"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifFieldsAreBiggerThanMaxLength() throws java.lang.Exception {
        java.lang.String invalidPayload = ((((((((((("{" + "\"username\": \"") + org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(256)) + "\",") + "\"password\": \"") + org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(256)) + "\",") + "\"email\": \"email@example.com\",") + "\"gateway_account_ids\": [\"1\"],") + "\"telephone_number\": \"07990000000\",") + "\"otp_key\": \"12345\",") + "\"role_name\": \"a-role\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.readTree(invalidPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(jsonNode);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [username] must have a maximum length of 255 characters"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnEmpty_ifAllValidationsArePassed() throws java.lang.Exception {
        java.lang.String validPayload = ((((((("{" + "\"username\": \"a-username\",") + "\"password\": \"a-password\",") + "\"email\": \"email@example.com\",") + "\"gateway_account_ids\": [\"1\"],") + "\"telephone_number\": \"01134960000\",") + "\"otp_key\": \"12345\",") + "\"role_name\": \"a-role\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.readTree(validPayload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateCreateRequest(jsonNode);
        junit.framework.TestCase.assertFalse(optionalErrors.isPresent());
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_ifValidSearchRequest_whenFindingAUser() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("username", "some-existing-user"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateFindRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_ifNoBody_whenValidateNewSecondFactorPasscodeRequest() {
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(null);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_ifProvisionalNotPresent_whenValidateNewSecondFactorPasscodeRequest() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Collections.emptyMap());
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_ifProvisionalTrue_whenValidateNewSecondFactorPasscodeRequest() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("provisional", true));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void shouldSuccess_ifProvisionalFalse_whenValidateNewSecondFactorPasscodeRequest() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("provisional", false));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(optionalErrors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifProvisionalNotBoolean_whenValidateNewSecondFactorPasscodeRequest() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("provisional", "maybe"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(payload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [provisional] must be a boolean"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifCodeMissing_whenValidate2faActivateRequest() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("second_factor", "SMS"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validate2faActivateRequest(payload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [code] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifCodeNotNumeric_whenValidate2faActivateRequest() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("code", "I am not a number, I’m a free man!", "second_factor", "SMS"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validate2faActivateRequest(payload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [code] must be a number"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifSecondFactorMissing_whenValidate2faActivateRequest() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("code", 123456));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validate2faActivateRequest(payload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [second_factor] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifSecondFactorInvalid_whenValidate2faActivateRequest() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.valueToTree(java.util.Map.of("code", 123456, "second_factor", "PINKY_SWEAR"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validate2faActivateRequest(payload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Invalid second_factor [PINKY_SWEAR]"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_ifRequiredFieldsMissing_whenFindingAUser() throws java.lang.Exception {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.UserRequestValidatorTest.objectMapper.readTree("{}");
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> optionalErrors = validator.validateFindRequest(payload);
        junit.framework.TestCase.assertTrue(optionalErrors.isPresent());
        uk.gov.pay.adminusers.utils.Errors errors = optionalErrors.get();
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors.getErrors(), org.hamcrest.Matchers.hasItems("Field [username] is required"));
    }

    private void mockValidValuesFor(com.fasterxml.jackson.databind.JsonNode mockJsonNode, java.util.Map<java.lang.String, java.lang.String> mockFieldValues) {
        for (java.util.Map.Entry<java.lang.String, java.lang.String> mockFieldValue : mockFieldValues.entrySet()) {
            com.fasterxml.jackson.databind.JsonNode fieldMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
            org.mockito.Mockito.when(fieldMock.asText()).thenReturn(mockFieldValue.getValue());
            org.mockito.Mockito.when(mockJsonNode.get(mockFieldValue.getKey())).thenReturn(fieldMock);
        }
        org.mockito.Mockito.when(mockJsonNode.fieldNames()).thenReturn(mockFieldValues.keySet().iterator());
    }
}
