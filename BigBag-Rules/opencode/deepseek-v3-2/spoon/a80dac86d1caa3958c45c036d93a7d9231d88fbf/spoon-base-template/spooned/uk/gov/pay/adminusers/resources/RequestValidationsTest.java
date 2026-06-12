package uk.gov.pay.adminusers.resources;
public class RequestValidationsTest {
    private uk.gov.pay.adminusers.validations.RequestValidations requestValidations = new uk.gov.pay.adminusers.validations.RequestValidations();

    private static final java.lang.String FIELD_1 = "field1";

    private static final java.lang.String FIELD_2 = "field2";

    @org.junit.jupiter.api.Test
    public void checkIfExistsOrEmpty_shouldSucceed_whenFieldsAreProvidedWithValues() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, "value1");
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2, "value2");
        java.util.Optional<java.util.List<java.lang.String>> errors = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void checkIfExistsOrEmpty_shouldFail_whenFieldsAreProvidedWithEmptyValues() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, "");
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2, "");
        java.util.Optional<java.util.List<java.lang.String>> errors = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void checkIfExistsOrEmpty_shouldFail_whenFieldsAreProvidedWithNullValues() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.set(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, null);
        payload.set(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2, null);
        java.util.Optional<java.util.List<java.lang.String>> errors = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void checkIfExists_shouldSucceed_whenFieldsAreProvidedWithValues() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, "value1");
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2, "value2");
        java.util.Optional<java.util.List<java.lang.String>> errors = requestValidations.checkExists(payload, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void checkIfExists_shouldSucceed_whenFieldsAreProvidedWithEmptyValues() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, "");
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2, " ");
        java.util.Optional<java.util.List<java.lang.String>> errors = requestValidations.checkExists(payload, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void checkIfExists_shouldFail_whenFieldsAreProvidedWithNullValues() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.set(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, null);
        payload.set(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2, null);
        java.util.Optional<java.util.List<java.lang.String>> errors = requestValidations.checkExists(payload, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void checkNotBoolean_shouldSucceed_whenFieldsAreTrueOrFalse() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, "true");
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2, "false");
        java.util.Optional<java.util.List<java.lang.String>> errors = requestValidations.checkIsBoolean(payload, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_2);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void checkNotBoolean_shouldFail_whenFieldsAreNotTrueOrFalse() {
        com.fasterxml.jackson.databind.node.ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put(uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1, "maybe");
        java.util.Optional<java.util.List<java.lang.String>> errors = requestValidations.checkIsBoolean(payload, uk.gov.pay.adminusers.resources.RequestValidationsTest.FIELD_1);
        org.hamcrest.MatcherAssert.assertThat(errors.isPresent(), org.hamcrest.core.Is.is(true));
    }
}
