package uk.gov.pay.adminusers.resources;
public class ResetPasswordResourceIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private static final java.lang.String RESET_PASSWORD_RESOURCE_URL = "/v1/api/reset-password";

    private static final java.lang.String CURRENT_PASSWORD = "myOldEncryptedPassword";

    private int userId;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        userId = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withPassword(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.CURRENT_PASSWORD).withUsername(username).withEmail(email).insertUser().getId();
    }

    @org.junit.jupiter.api.Test
    public void resetPassword_shouldReturn204_whenCodeIsValid_changingTheOldPasswordToTheNewEncryptedOne() throws java.lang.Exception {
        java.lang.String forgottenPasswordCode = uk.gov.pay.adminusers.fixtures.ForgottenPasswordDbFixture.forgottenPasswordDbFixture(databaseHelper, userId).insertForgottenPassword();
        java.lang.String password = "iPromiseIWon'tForgetThisPassword";
        java.util.Map<java.lang.Object, java.lang.Object> payload = java.util.Map.of("forgotten_password_code", forgottenPasswordCode, "new_password", password);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.RESET_PASSWORD_RESOURCE_URL).then().statusCode(204);
        java.util.Map<java.lang.String, java.lang.Object> userAttributes = databaseHelper.findUser(userId).get(0);
        java.lang.Object userPassword = userAttributes.get("password");
        org.hamcrest.MatcherAssert.assertThat(userPassword, org.hamcrest.core.Is.is(org.hamcrest.CoreMatchers.notNullValue()));
        org.hamcrest.MatcherAssert.assertThat(userPassword, org.hamcrest.core.Is.is(org.hamcrest.CoreMatchers.not(password)));
        org.hamcrest.MatcherAssert.assertThat(userPassword, org.hamcrest.core.Is.is(org.hamcrest.CoreMatchers.not(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.CURRENT_PASSWORD)));
    }

    @org.junit.jupiter.api.Test
    public void resetPassword_shouldReturn400_whenCodeIsInvalid_andCurrentEncryptedPasswordShouldNotChange() throws java.lang.Exception {
        java.util.Map<java.lang.Object, java.lang.Object> payload = java.util.Map.of("forgotten_password_code", "aCodeThatDoesNotExist", "new_password", "iPromiseIWon'tForgetThisPassword");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.RESET_PASSWORD_RESOURCE_URL).then().statusCode(404).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Field [forgotten_password_code] non-existent/expired"));
        java.util.Map<java.lang.String, java.lang.Object> userAttributes = databaseHelper.findUser(userId).get(0);
        java.lang.Object userPassword = userAttributes.get("password");
        org.hamcrest.MatcherAssert.assertThat(userPassword, org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.CURRENT_PASSWORD));
    }

    @org.junit.jupiter.api.Test
    public void resetPassword_shouldReturn400_whenCodeHasExpired_andCurrentEncryptedPasswordShouldNotChange() throws java.lang.Exception {
        java.lang.String expiredForgottenPasswordCode = uk.gov.pay.adminusers.fixtures.ForgottenPasswordDbFixture.forgottenPasswordDbFixture(databaseHelper, userId).expired().insertForgottenPassword();
        java.util.Map<java.lang.Object, java.lang.Object> payload = java.util.Map.of("forgotten_password_code", expiredForgottenPasswordCode, "new_password", "iPromiseIWon'tForgetThisPassword");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.RESET_PASSWORD_RESOURCE_URL).then().statusCode(404).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Field [forgotten_password_code] non-existent/expired"));
        java.util.Map<java.lang.String, java.lang.Object> userAttributes = databaseHelper.findUser(userId).get(0);
        java.lang.Object userPassword = userAttributes.get("password");
        org.hamcrest.MatcherAssert.assertThat(userPassword, org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.CURRENT_PASSWORD));
    }

    @org.junit.jupiter.api.Test
    public void resetPassword_shouldReturn400_whenJsonIsMissing() {
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.RESET_PASSWORD_RESOURCE_URL).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("invalid JSON"));
    }

    @org.junit.jupiter.api.Test
    public void resetPassword_shouldReturn400_whenFieldsAreMissing() {
        givenSetup().when().body("{}").contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.ResetPasswordResourceIT.RESET_PASSWORD_RESOURCE_URL).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(2)).body("errors[0]", org.hamcrest.core.Is.is("Field [forgotten_password_code] is required")).body("errors[1]", org.hamcrest.core.Is.is("Field [new_password] is required"));
    }
}
