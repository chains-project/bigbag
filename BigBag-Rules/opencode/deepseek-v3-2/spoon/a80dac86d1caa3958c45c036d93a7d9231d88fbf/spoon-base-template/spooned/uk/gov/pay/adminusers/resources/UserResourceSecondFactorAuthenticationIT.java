package uk.gov.pay.adminusers.resources;
public class UserResourceSecondFactorAuthenticationIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private static final java.lang.String USER_2FA_AUTHENTICATE_URL = uk.gov.pay.adminusers.resources.IntegrationTest.USER_2FA_URL + "/authenticate";

    private static final java.lang.String OTP_KEY = "34f34";

    private java.lang.String externalId;

    private java.lang.String username;

    @org.junit.jupiter.api.BeforeEach
    public void createValidUser() {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withOtpKey(uk.gov.pay.adminusers.resources.UserResourceSecondFactorAuthenticationIT.OTP_KEY).withUsername(username).withEmail(email).insertUser();
        this.externalId = user.getExternalId();
        this.username = user.getUsername();
    }

    @org.junit.jupiter.api.Test
    public void shouldCreate2FA_forAValidNewSecondFactorPasscodeRequest_withNoBody() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_2FA_URL, externalId)).then().statusCode(200);
    }

    @org.junit.jupiter.api.Test
    public void shouldCreate2FA_forAValidNewSecondFactorPasscodeRequest_withProvisionalFalse() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.util.Map<java.lang.String, java.lang.Boolean> body = java.util.Map.of("provisional", false);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(body)).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_2FA_URL, externalId)).then().statusCode(200);
    }

    @org.junit.jupiter.api.Test
    public void shouldCreate2FA_forAValidNewSecondFactorPasscodeRequest_withProvisionalTrue() throws com.fasterxml.jackson.core.JsonProcessingException {
        databaseHelper.updateProvisionalOtpKey(username, "ABCDEFGHIJKLMNOP");
        java.util.Map<java.lang.String, java.lang.Boolean> body = java.util.Map.of("provisional", true);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(body)).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_2FA_URL, externalId)).then().statusCode(200);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnNotFound_forAValidNewSecondFactorPasscodeRequest_withProvisionalTrue_ifNoProvisionalOtpKey() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.util.Map<java.lang.String, java.lang.Boolean> body = java.util.Map.of("provisional", true);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(body)).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_2FA_URL, externalId)).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    public void shouldAuthenticate2FA_forAValid2FAAuthRequest() throws java.lang.Exception {
        com.warrenstrange.googleauth.GoogleAuthenticator testAuthenticator = new com.warrenstrange.googleauth.GoogleAuthenticator();
        int passcode = testAuthenticator.getTotpPassword(com.google.common.io.BaseEncoding.base32().encode(uk.gov.pay.adminusers.resources.UserResourceSecondFactorAuthenticationIT.OTP_KEY.getBytes()));
        java.util.Map<java.lang.String, java.lang.Integer> authBody = java.util.Map.of("code", passcode);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(authBody)).post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorAuthenticationIT.USER_2FA_AUTHENTICATE_URL, externalId)).then().statusCode(200).body("username", org.hamcrest.core.Is.is(username)).body("login_counter", org.hamcrest.core.Is.is(0)).body("disabled", org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnNotFound_forNonExistentUser_when2FAAuthCreateRequest() {
        java.lang.String nonExistingExternalId = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_2FA_URL, nonExistingExternalId)).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnUnauthorized_onInvalid2FACode_during2FAAuth() throws java.lang.Exception {
        int invalidPasscode = 111111;
        java.util.Map<java.lang.String, java.lang.Integer> authBody = java.util.Map.of("code", invalidPasscode);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(authBody)).post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorAuthenticationIT.USER_2FA_AUTHENTICATE_URL, externalId)).then().statusCode(401);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnUnauthorizedAndAccountLocked_during2FAAuth_ifMaxRetryExceeded() throws java.lang.Exception {
        databaseHelper.updateLoginCount(username, 10);
        int invalidPasscode = 111111;
        java.util.Map<java.lang.String, java.lang.Integer> authBody = java.util.Map.of("code", invalidPasscode);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(authBody)).post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorAuthenticationIT.USER_2FA_AUTHENTICATE_URL, externalId)).then().statusCode(401);
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, externalId)).then().statusCode(200).body("username", org.hamcrest.core.Is.is(username)).body("login_counter", org.hamcrest.core.Is.is(11)).body("disabled", org.hamcrest.core.Is.is(true));
    }
}
