package uk.gov.pay.adminusers.resources;
public class ForgottenPasswordResourceIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private static final java.lang.String FORGOTTEN_PASSWORDS_RESOURCE_URL = "/v1/api/forgotten-passwords";

    @org.junit.jupiter.api.Test
    public void shouldGetForgottenPasswordReference_whenCreate_forAnExistingUser() throws java.lang.Exception {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser().getUsername();
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(java.util.Map.of("username", username))).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.ForgottenPasswordResourceIT.FORGOTTEN_PASSWORDS_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn404_whenCreate_forNonExistingUser() throws java.lang.Exception {
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(java.util.Map.of("username", "non-existent-user"))).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.ForgottenPasswordResourceIT.FORGOTTEN_PASSWORDS_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void shouldGetForgottenPassword_whenGetByCode_forAnExistingForgottenPassword() {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        int userId = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser().getId();
        java.lang.String forgottenPasswordCode = uk.gov.pay.adminusers.fixtures.ForgottenPasswordDbFixture.forgottenPasswordDbFixture(databaseHelper, userId).insertForgottenPassword();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get((uk.gov.pay.adminusers.resources.ForgottenPasswordResourceIT.FORGOTTEN_PASSWORDS_RESOURCE_URL + "/") + forgottenPasswordCode).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode()).body("code", org.hamcrest.core.Is.is(forgottenPasswordCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn404_whenGetByCode_forNonExistingForgottenPassword() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(uk.gov.pay.adminusers.resources.ForgottenPasswordResourceIT.FORGOTTEN_PASSWORDS_RESOURCE_URL + "/non-existent-code").then().statusCode(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn404_whenGetByCode_andCodeExceedsMaxLength() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get((uk.gov.pay.adminusers.resources.ForgottenPasswordResourceIT.FORGOTTEN_PASSWORDS_RESOURCE_URL + "/") + org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(256)).then().statusCode(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }
}
