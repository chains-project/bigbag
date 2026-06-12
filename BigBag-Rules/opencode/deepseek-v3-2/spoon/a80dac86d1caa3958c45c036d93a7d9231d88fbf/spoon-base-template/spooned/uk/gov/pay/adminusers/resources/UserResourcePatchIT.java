package uk.gov.pay.adminusers.resources;
class UserResourcePatchIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private java.lang.String externalId;

    @org.junit.jupiter.api.BeforeEach
    void createAUser() {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser();
        externalId = user.getExternalId();
    }

    @org.junit.jupiter.api.Test
    void shouldIncreaseSessionVersion_whenPatchAttempt() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "append", "path", "sessionVersion", "value", 2));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, externalId)).then().statusCode(200).body("session_version", org.hamcrest.core.Is.is(2));
    }

    @org.junit.jupiter.api.Test
    void shouldUpdateEmail_whenPatchAttempt() {
        java.lang.String newEmail = "iLoveGovUkPay@example.com";
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "replace", "path", "email", "value", newEmail));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, externalId)).then().statusCode(200).body("email", org.hamcrest.core.Is.is(newEmail)).body("username", org.hamcrest.core.Is.is(newEmail));
    }

    @org.junit.jupiter.api.Test
    void shouldUpdateTelephoneNumber_whenPatchAttempt() {
        java.lang.String newTelephoneNumber = "+441134960000";
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "replace", "path", "telephone_number", "value", newTelephoneNumber));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, externalId)).then().statusCode(200).body("telephone_number", org.hamcrest.core.Is.is(newTelephoneNumber));
    }

    @org.junit.jupiter.api.Test
    void shouldUpdateFeatures_whenPatchAttempt() {
        java.lang.String newFeatures = "SUPER_FEATURE_1, SECRET_SQUIRREL_FEATURE";
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "replace", "path", "features", "value", newFeatures));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, externalId)).then().statusCode(200).body("features", org.hamcrest.core.Is.is(newFeatures));
    }

    @org.junit.jupiter.api.Test
    void shouldDisableUser_whenPatchAttempt() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "replace", "path", "disabled", "value", "true"));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, externalId)).then().statusCode(200).body("disabled", org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn404_whenUnknownExternalIdIsSupplied() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "append", "path", "sessionVersion", "value", 1));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, "whatever")).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    void shouldError_whenPatchRequiredFieldsAreMissing() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("blah", "sessionVersion", "value", 1));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, externalId)).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(2)).body("errors[0]", org.hamcrest.core.Is.is("Field [op] is required")).body("errors[1]", org.hamcrest.core.Is.is("Field [path] is required"));
    }
}
