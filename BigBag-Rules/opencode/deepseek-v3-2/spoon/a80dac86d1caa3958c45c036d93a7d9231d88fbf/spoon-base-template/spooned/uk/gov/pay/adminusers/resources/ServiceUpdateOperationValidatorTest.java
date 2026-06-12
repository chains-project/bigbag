package uk.gov.pay.adminusers.resources;
public class ServiceUpdateOperationValidatorTest {
    private static final java.lang.String GO_LIVE_STAGE_INVALID_ERROR_MESSAGE = "Field [value] must be one of [NOT_STARTED, ENTERED_ORGANISATION_NAME, ENTERED_ORGANISATION_ADDRESS, CHOSEN_PSP_STRIPE, CHOSEN_PSP_WORLDPAY, CHOSEN_PSP_SMARTPAY, CHOSEN_PSP_EPDQ, CHOSEN_PSP_GOV_BANKING_WORLDPAY, TERMS_AGREED_STRIPE, TERMS_AGREED_WORLDPAY, TERMS_AGREED_SMARTPAY, TERMS_AGREED_EPDQ, TERMS_AGREED_GOV_BANKING_WORLDPAY, DENIED, LIVE]";

    private static final java.lang.String PSP_TEST_ACCOUNT_STAGE_INVALID_ERROR_MESSAGE = "Field [value] must be one of [NOT_STARTED, REQUEST_SUBMITTED, CREATED]";

    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private final uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator serviceUpdateOperationValidator = new uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator(new uk.gov.pay.adminusers.validations.RequestValidations());

    @org.junit.jupiter.api.Test
    public void shouldFail_whenUpdate_whenMissingRequiredField() {
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("value", "example-name");
        java.util.List<java.lang.String> errors = serviceUpdateOperationValidator.validate(mapper.valueToTree(payload));
        org.hamcrest.MatcherAssert.assertThat(errors.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(errors, org.hamcrest.CoreMatchers.hasItem("Field [path] is required"));
        org.hamcrest.MatcherAssert.assertThat(errors, org.hamcrest.CoreMatchers.hasItem("Field [op] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_whenUpdate_whenInvalidPath() {
        shouldFail("xyz", "replace", "any value", "Path [xyz] is invalid");
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_whenUpdateServiceName_whenPathContainsUnsupportedLanguage() {
        shouldFail("service_name/xx", "replace", "example name", "Path [service_name/xx] is invalid");
    }

    private static java.lang.Object[] shouldFailForOperationParams() {
        return new java.lang.Object[]{ new java.lang.Object[]{ "replace", "gateway_account_ids", java.util.List.of(1, 2) }, new java.lang.Object[]{ "add", "collect_billing_address", false }, new java.lang.Object[]{ "add", "experimental_features_enabled", true }, new java.lang.Object[]{ "add", "redirect_to_service_immediately_on_terminal_state", true }, new java.lang.Object[]{ "add", "collect_billing_address", true }, new java.lang.Object[]{ "add", "current_go_live_stage", "CHOSEN_PSP_STRIPE" }, new java.lang.Object[]{ "add", "current_psp_test_account_stage", "REQUEST_SUBMITTED" }, new java.lang.Object[]{ "add", "experimental_features_enabled", false }, new java.lang.Object[]{ "add", "agent_initiated_moto_enabled", false }, new java.lang.Object[]{ "add", "sector", "any value" }, new java.lang.Object[]{ "add", "internal", false }, new java.lang.Object[]{ "add", "archived", false }, new java.lang.Object[]{ "add", "went_live_date", "2020-01-01T01:01:00Z" }, new java.lang.Object[]{ "add", "merchant_details/name", "any value" }, new java.lang.Object[]{ "add", "merchant_details/address_line1", "any value" }, new java.lang.Object[]{ "add", "merchant_details/address_line2", "any value" }, new java.lang.Object[]{ "add", "merchant_details/address_city", "any value" }, new java.lang.Object[]{ "add", "merchant_details/address_country", "any value" }, new java.lang.Object[]{ "add", "merchant_details/address_postcode", "any value" }, new java.lang.Object[]{ "add", "merchant_details/email", "any value" }, new java.lang.Object[]{ "add", "merchant_details/telephone_number", "any value" }, new java.lang.Object[]{ "add", "default_billing_address_country", "GB" } };
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("shouldFailForOperationParams")
    public void shouldFailForOperation(java.lang.String operation, java.lang.String path, java.lang.Object value) {
        java.lang.String expectedErrorMessage = java.lang.String.format("Operation [%s] is invalid for path [%s]", operation, path);
        shouldFail(path, operation, value, expectedErrorMessage);
    }

    private static java.lang.Object[] replaceShouldFailWhenValueInvalidValueParameters() {
        return new java.lang.Object[]{ new java.lang.Object[]{ "service_name/en", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "sector", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "current_go_live_stage", 42, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidatorTest.GO_LIVE_STAGE_INVALID_ERROR_MESSAGE }, new java.lang.Object[]{ "current_go_live_stage", "CAKE_ORDERED", uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidatorTest.GO_LIVE_STAGE_INVALID_ERROR_MESSAGE }, new java.lang.Object[]{ "current_psp_test_account_stage", "42", uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidatorTest.PSP_TEST_ACCOUNT_STAGE_INVALID_ERROR_MESSAGE }, new java.lang.Object[]{ "merchant_details/name", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "merchant_details/address_line1", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "merchant_details/address_line2", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "merchant_details/address_city", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "merchant_details/address_country", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "merchant_details/address_postcode", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "merchant_details/email", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "merchant_details/telephone_number", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "collect_billing_address", "a string", "Field [value] must be a boolean" }, new java.lang.Object[]{ "redirect_to_service_immediately_on_terminal_state", "a string", "Field [value] must be a boolean" }, new java.lang.Object[]{ "experimental_features_enabled", "a string", "Field [value] must be a boolean" }, new java.lang.Object[]{ "agent_initiated_moto_enabled", 42, "Field [value] must be a boolean" }, new java.lang.Object[]{ "internal", "a string", "Field [value] must be a boolean" }, new java.lang.Object[]{ "archived", "a string", "Field [value] must be a boolean" }, new java.lang.Object[]{ "custom_branding", "a string", "Value for path [custom_branding] must be a JSON" }, new java.lang.Object[]{ "went_live_date", 42, "Field [value] must be a valid date time with timezone" }, new java.lang.Object[]{ "went_live_date", "a string", "Field [value] must be a valid date time with timezone" }, new java.lang.Object[]{ "default_billing_address_country", 42, "Field [value] must be a string" }, new java.lang.Object[]{ "default_billing_address_country", "too long", "Field [value] must have a maximum length of 2 characters" } };
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("replaceShouldFailWhenValueInvalidValueParameters")
    public void replaceShouldFailWhenInvalidValue(java.lang.String path, java.lang.Object value, java.lang.String expectedErrorMessage) {
        shouldFail(path, "replace", value, expectedErrorMessage);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "redirect_to_service_immediately_on_terminal_state", "collect_billing_address", "current_go_live_stage", "current_psp_test_account_stage", "experimental_features_enabled", "agent_initiated_moto_enabled", "merchant_details/name", "merchant_details/address_line1", "merchant_details/address_line2", "merchant_details/address_city", "merchant_details/address_country", "merchant_details/address_postcode", "merchant_details/email", "merchant_details/telephone_number", "custom_branding", "sector", "internal", "archived", "went_live_date" })
    public void replaceShouldFailWhenValueMissing(java.lang.String path) {
        com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.createObjectNode();
        payload.put("path", path);
        payload.put("op", "replace");
        java.util.List<java.lang.String> errors = serviceUpdateOperationValidator.validate(payload);
        org.hamcrest.MatcherAssert.assertThat(errors.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors, org.hamcrest.CoreMatchers.hasItem("Field [value] is required"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "redirect_to_service_immediately_on_terminal_state", "collect_billing_address", "current_go_live_stage", "current_psp_test_account_stage", "experimental_features_enabled", "agent_initiated_moto_enabled", "merchant_details/name", "merchant_details/address_line1", "merchant_details/address_line2", "merchant_details/address_city", "merchant_details/address_country", "merchant_details/address_postcode", "merchant_details/email", "merchant_details/telephone_number", "custom_branding", "sector", "internal", "archived", "went_live_date" })
    public void replaceShouldFailWhenValueIsNull(java.lang.String path) {
        com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.createObjectNode();
        payload.put("path", path);
        payload.put("op", "replace");
        payload.putNull("value");
        java.util.List<java.lang.String> errors = serviceUpdateOperationValidator.validate(payload);
        org.hamcrest.MatcherAssert.assertThat(errors.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors, org.hamcrest.CoreMatchers.hasItem("Field [value] is required"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "service_name/en", "current_go_live_stage", "current_psp_test_account_stage", "merchant_details/name", "merchant_details/address_line1", "merchant_details/address_city", "merchant_details/address_country", "merchant_details/address_postcode" })
    public void replaceShouldFailWhenValueIsEmptyString(java.lang.String path) {
        shouldFail(path, "replace", "", "Field [value] is required");
    }

    private static java.lang.Object[] shouldFailWhenStringValueIsTooLongParameters() {
        return new java.lang.Object[]{ new java.lang.Object[]{ "service_name/en", 50 }, new java.lang.Object[]{ "merchant_details/name", 255 }, new java.lang.Object[]{ "merchant_details/address_line1", 255 }, new java.lang.Object[]{ "merchant_details/address_line2", 255 }, new java.lang.Object[]{ "merchant_details/address_city", 255 }, new java.lang.Object[]{ "merchant_details/address_country", 10 }, new java.lang.Object[]{ "merchant_details/address_postcode", 25 }, new java.lang.Object[]{ "merchant_details/email", 255 }, new java.lang.Object[]{ "merchant_details/telephone_number", 255 }, new java.lang.Object[]{ "sector", 50 } };
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("shouldFailWhenStringValueIsTooLongParameters")
    public void replaceShouldFailWhenStringValueIsTooLong(java.lang.String path, int expectedMaxLength) {
        shouldFail(path, "replace", org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(expectedMaxLength + 1), java.lang.String.format("Field [value] must have a maximum length of %s characters", expectedMaxLength));
    }

    private static java.lang.Object[] shouldSucceedParams() {
        return new java.lang.Object[]{ new java.lang.Object[]{ "add", "gateway_account_ids", java.util.List.of(1, 2) }, new java.lang.Object[]{ "replace", "redirect_to_service_immediately_on_terminal_state", true }, new java.lang.Object[]{ "replace", "experimental_features_enabled", true }, new java.lang.Object[]{ "replace", "agent_initiated_moto_enabled", true }, new java.lang.Object[]{ "replace", "collect_billing_address", true }, new java.lang.Object[]{ "replace", "current_go_live_stage", "CHOSEN_PSP_STRIPE" }, new java.lang.Object[]{ "replace", "current_psp_test_account_stage", "REQUEST_SUBMITTED" }, new java.lang.Object[]{ "replace", "merchant_details/name", "Bob's Building Business" }, new java.lang.Object[]{ "replace", "merchant_details/address_line1", "1 Builders Avenue" }, new java.lang.Object[]{ "replace", "merchant_details/address_line2", "" }, new java.lang.Object[]{ "replace", "merchant_details/address_city", "Burnley" }, new java.lang.Object[]{ "replace", "merchant_details/address_country", "GB" }, new java.lang.Object[]{ "replace", "merchant_details/address_postcode", "B52 9EG" }, new java.lang.Object[]{ "replace", "merchant_details/email", "" }, new java.lang.Object[]{ "replace", "merchant_details/email", "bob-the-builder@example.com" }, new java.lang.Object[]{ "replace", "merchant_details/telephone_number", "" }, new java.lang.Object[]{ "replace", "merchant_details/telephone_number", "00000000000" }, new java.lang.Object[]{ "replace", "custom_branding", java.util.Map.of("image_url", "image url", "css_url", "css url") }, new java.lang.Object[]{ "replace", "custom_branding", java.util.Collections.emptyMap() }, new java.lang.Object[]{ "replace", "service_name/en", "example-name" }, new java.lang.Object[]{ "replace", "sector", "local government" }, new java.lang.Object[]{ "replace", "internal", true }, new java.lang.Object[]{ "replace", "archived", true }, new java.lang.Object[]{ "replace", "went_live_date", "2020-01-01T01:01:00Z" }, new java.lang.Object[]{ "replace", "default_billing_address_country", null }, new java.lang.Object[]{ "replace", "default_billing_address_country", "GB" } };
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("shouldSucceedParams")
    public void shouldSucceed(java.lang.String operation, java.lang.String path, java.lang.Object value) {
        java.util.Map<java.lang.String, java.lang.Object> payload = new java.util.HashMap<>();
        payload.put("path", path);
        payload.put("op", operation);
        payload.put("value", value);
        java.util.List<java.lang.String> errors = serviceUpdateOperationValidator.validate(mapper.valueToTree(payload));
        org.hamcrest.MatcherAssert.assertThat(errors.size(), org.hamcrest.core.Is.is(0));
    }

    private void shouldFail(java.lang.String path, java.lang.String operation, java.lang.Object value, java.lang.String expectedErrorMessage) {
        com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.valueToTree(java.util.Map.of("path", path, "op", operation, "value", value));
        java.util.List<java.lang.String> errors = serviceUpdateOperationValidator.validate(payload);
        org.hamcrest.MatcherAssert.assertThat(errors.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errors, org.hamcrest.CoreMatchers.hasItem(expectedErrorMessage));
    }
}
