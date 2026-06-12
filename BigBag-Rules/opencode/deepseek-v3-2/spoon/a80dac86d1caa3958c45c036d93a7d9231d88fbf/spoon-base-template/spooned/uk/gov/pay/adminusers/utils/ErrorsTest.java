package uk.gov.pay.adminusers.utils;
class ErrorsTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.Test
    void shouldMarshalMultipleErrorsCorrectly() throws java.lang.Exception {
        java.util.List<java.lang.String> errorList = java.util.List.of("Error 1", "Error 2", "Error 3");
        uk.gov.pay.adminusers.utils.Errors errors = uk.gov.pay.adminusers.utils.Errors.from(errorList);
        java.lang.String errorsJson = uk.gov.pay.adminusers.utils.ErrorsTest.objectMapper.writeValueAsString(errors);
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> response = uk.gov.pay.adminusers.utils.ErrorsTest.objectMapper.readValue(errorsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        org.hamcrest.MatcherAssert.assertThat(response.get("errors"), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(response.get("errors").size(), org.hamcrest.core.Is.is(3));
        org.hamcrest.MatcherAssert.assertThat(response.get("errors"), org.hamcrest.Matchers.hasItems("Error 1", "Error 2", "Error 3"));
    }

    @org.junit.jupiter.api.Test
    void shouldMarshalSingleErrorsCorrectly() throws java.lang.Exception {
        uk.gov.pay.adminusers.utils.Errors errors = uk.gov.pay.adminusers.utils.Errors.from("an error");
        java.lang.String errorsJson = uk.gov.pay.adminusers.utils.ErrorsTest.objectMapper.writeValueAsString(errors);
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> response = uk.gov.pay.adminusers.utils.ErrorsTest.objectMapper.readValue(errorsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        org.hamcrest.MatcherAssert.assertThat(response.get("errors"), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(response.get("errors").size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(response.get("errors"), org.hamcrest.Matchers.hasItem("an error"));
    }
}
