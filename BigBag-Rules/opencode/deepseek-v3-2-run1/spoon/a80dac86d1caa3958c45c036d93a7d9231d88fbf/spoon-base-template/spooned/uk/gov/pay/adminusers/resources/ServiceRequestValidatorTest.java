package uk.gov.pay.adminusers.resources;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class ServiceRequestValidatorTest {
    private static final java.lang.String DEFAULT_MERCHANT_DETAILS_NAME = "Merchant name";

    private static final java.lang.String DEFAULT_MERCHANT_DETAILS_EMAIL = "merchant-user@example.com";

    @org.mockito.Mock
    private uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator mockServiceUpdateOperationValidator;

    private uk.gov.pay.adminusers.resources.ServiceRequestValidator serviceRequestValidator;

    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        serviceRequestValidator = new uk.gov.pay.adminusers.resources.ServiceRequestValidator(new uk.gov.pay.adminusers.validations.RequestValidations(), mockServiceUpdateOperationValidator);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenValidateUpdateAttributeRequestSucceeds_withSingleOperation() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createUpdateOperation("this", "will", "succeed");
        org.mockito.BDDMockito.given(mockServiceUpdateOperationValidator.validate(payload)).willReturn(java.util.Collections.emptyList());
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateUpdateAttributeRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_whenValidateUpdateAttributeRequestFails_withSingleOperation() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createUpdateOperation("this", "will", "fail");
        org.mockito.BDDMockito.given(mockServiceUpdateOperationValidator.validate(payload)).willReturn(java.util.Arrays.asList("Error 1", "Error 2"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateUpdateAttributeRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors(), org.hamcrest.CoreMatchers.hasItem("Error 1"));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors(), org.hamcrest.CoreMatchers.hasItem("Error 2"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenValidateUpdateAttributeRequestSucceeds_withArrayOfOperations() {
        com.fasterxml.jackson.databind.node.ObjectNode operation1 = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createUpdateOperation("the", "first", "operation");
        com.fasterxml.jackson.databind.node.ObjectNode operation2 = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createUpdateOperation("the", "second", "operation");
        com.fasterxml.jackson.databind.node.ArrayNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode().add(operation1).add(operation2);
        org.mockito.BDDMockito.given(mockServiceUpdateOperationValidator.validate(operation1)).willReturn(java.util.Collections.emptyList());
        org.mockito.BDDMockito.given(mockServiceUpdateOperationValidator.validate(operation2)).willReturn(java.util.Collections.emptyList());
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateUpdateAttributeRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_whenValidateUpdateAttributeRequestFails_withArrayOfOperations() {
        com.fasterxml.jackson.databind.node.ObjectNode operation1 = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createUpdateOperation("the", "first", "operation");
        com.fasterxml.jackson.databind.node.ObjectNode operation2 = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createUpdateOperation("the", "second", "operation");
        com.fasterxml.jackson.databind.node.ObjectNode operation3 = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createUpdateOperation("the", "third", "operation");
        com.fasterxml.jackson.databind.node.ArrayNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode().add(operation1).add(operation2).add(operation3);
        org.mockito.BDDMockito.given(mockServiceUpdateOperationValidator.validate(operation1)).willReturn(java.util.Collections.emptyList());
        org.mockito.BDDMockito.given(mockServiceUpdateOperationValidator.validate(operation2)).willReturn(java.util.Arrays.asList("Error 1", "Error 2"));
        org.mockito.BDDMockito.given(mockServiceUpdateOperationValidator.validate(operation3)).willReturn(java.util.Collections.singletonList("Error 3"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateUpdateAttributeRequest(payload);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().size(), org.hamcrest.core.Is.is(3));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors(), org.hamcrest.CoreMatchers.hasItem("Error 1"));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors(), org.hamcrest.CoreMatchers.hasItem("Error 2"));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors(), org.hamcrest.CoreMatchers.hasItem("Error 3"));
    }

    @org.junit.jupiter.api.Test
    public void shouldAllowNonNumericGatewayAccounts_whenFindingServices() {
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateFindRequest("non-numeric-id");
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldAllowWellFormedRequest_whenSearchingServices() throws com.fasterxml.jackson.core.JsonProcessingException {
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(mapper.readTree("{\"service_name\": \"test name\", \"service_merchant_name\": \"test merchant name\"}"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateSearchRequest(searchRequest);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldErrorIfSpecialCharsArePresent_whenSearchingServices() throws com.fasterxml.jackson.core.JsonProcessingException {
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(mapper.readTree("{\"service_name\": \"!@£\"}"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateSearchRequest(searchRequest);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().get(0), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_SPECIAL_CHARS_ERR_MSG));
    }

    @org.junit.jupiter.api.Test
    public void shouldErrorIfSearchStringIsTooLong_whenSearchingServices() throws com.fasterxml.jackson.core.JsonProcessingException {
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(mapper.readTree("{\"service_name\": \"\", \"service_merchant_name\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"));
        org.hamcrest.MatcherAssert.assertThat(searchRequest.getServiceMerchantNameSearchString().length(), org.hamcrest.core.Is.is(org.hamcrest.Matchers.greaterThan(60)));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateSearchRequest(searchRequest);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().get(0), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_LENGTH_ERR_MSG));
    }

    @org.junit.jupiter.api.Test
    public void shouldErrorIfIfAllParamsEmpty_whenSearchingServices() throws com.fasterxml.jackson.core.JsonProcessingException {
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(mapper.readTree("{\"service_name\": \"\",\"service_merchant_name\": \"\"}"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateSearchRequest(searchRequest);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().get(0), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_SUPPORT_ERR_MSG));
    }

    @org.junit.jupiter.api.Test
    public void shouldErrorIfUnrecognisedParams_whenSearchingServices() throws com.fasterxml.jackson.core.JsonProcessingException {
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(mapper.readTree("{\"random_one\": \"some text\",\"random_two\": \"some more text\"}"));
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = serviceRequestValidator.validateSearchRequest(searchRequest);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(errors.get().getErrors().get(0), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_SUPPORT_ERR_MSG));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_updatingMerchantDetails() throws uk.gov.pay.adminusers.exception.ValidationException {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_NAME, uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.DEFAULT_MERCHANT_DETAILS_NAME);
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_LINE1, "line1");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_CITY, "city");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_COUNTRY, "country");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE, "postcode");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_EMAIL, uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.DEFAULT_MERCHANT_DETAILS_EMAIL);
        serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload);
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_updatingMerchantDetails_forEmptyObject() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ValidationException.class, () -> serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_updatingMerchantDetails_forMissingMandatoryFields() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_LINE1, "line1");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_CITY, "city");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_COUNTRY, "country");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE, "postcode");
        org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ValidationException.class, () -> serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_updatingMerchantDetails_forBlankStringMandatoryFields() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_NAME, "");
        org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ValidationException.class, () -> serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_updatingMerchantDetails_forNullValueMandatoryFields() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.set(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_NAME, null);
        org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ValidationException.class, () -> serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_updatingMerchantDetails_whenInvalidEmail() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createMerchantDetailsJsonPayload(uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.DEFAULT_MERCHANT_DETAILS_NAME, "invalid@example.com-uk");
        uk.gov.pay.adminusers.exception.ValidationException validationException = org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ValidationException.class, () -> serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload));
        org.hamcrest.MatcherAssert.assertThat(validationException.getErrors().getErrors(), org.hamcrest.CoreMatchers.hasItem("Field [email] must be a valid email address"));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_updatingMerchantDetails_whenEmailOver255() {
        java.lang.String longEmail = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(256);
        com.fasterxml.jackson.databind.node.ObjectNode payload = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createMerchantDetailsJsonPayload(uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.DEFAULT_MERCHANT_DETAILS_NAME, longEmail);
        uk.gov.pay.adminusers.exception.ValidationException validationException = org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ValidationException.class, () -> serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload));
        org.hamcrest.MatcherAssert.assertThat(validationException.getErrors().getErrors(), org.hamcrest.CoreMatchers.hasItem("Field [email] must have a maximum length of 255 characters"));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_updatingMerchantDetails_whenNameOver255() {
        java.lang.String longName = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(256);
        com.fasterxml.jackson.databind.node.ObjectNode payload = uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.createMerchantDetailsJsonPayload(longName, uk.gov.pay.adminusers.resources.ServiceRequestValidatorTest.DEFAULT_MERCHANT_DETAILS_EMAIL);
        uk.gov.pay.adminusers.exception.ValidationException validationException = org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ValidationException.class, () -> serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload));
        org.hamcrest.MatcherAssert.assertThat(validationException.getErrors().getErrors(), org.hamcrest.CoreMatchers.hasItem("Field [name] must have a maximum length of 255 characters"));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode createMerchantDetailsJsonPayload(java.lang.String name, java.lang.String email) {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_NAME, name);
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_LINE1, "line1");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_CITY, "city");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_COUNTRY, "country");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE, "postcode");
        payload.put(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_EMAIL, email);
        return payload;
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode createUpdateOperation(java.lang.String path, java.lang.String op, java.lang.String value) {
        com.fasterxml.jackson.databind.node.ObjectNode operation = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        operation.put(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_PATH, path);
        operation.put(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_OP, op);
        operation.put(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE, value);
        return operation;
    }
}
