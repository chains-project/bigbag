package uk.gov.pay.adminusers.resources;
public class UserResourceSecondFactorProvisioningIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private static final java.lang.String USER_2FA_PROVISION_URL = uk.gov.pay.adminusers.resources.IntegrationTest.USER_2FA_URL + "/provision";

    private static final java.lang.String USER_2FA_ACTIVATE_URL = uk.gov.pay.adminusers.resources.IntegrationTest.USER_2FA_URL + "/activate";

    private static final java.lang.String ORIGINAL_OTP_KEY = "1111111111111111";

    private java.lang.String externalId;

    private java.lang.String username;

    @org.junit.jupiter.api.BeforeEach
    public void createValidUser() {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withOtpKey(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.ORIGINAL_OTP_KEY).withUsername(username).withEmail(email).insertUser();
        this.externalId = user.getExternalId();
        this.username = user.getUsername();
    }

    @org.junit.jupiter.api.Test
    public void shouldProvisionNewOtpKey() {
        givenSetup().when().post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.USER_2FA_PROVISION_URL, externalId)).then().statusCode(200).body("username", org.hamcrest.Matchers.is(username)).body("otp_key", org.hamcrest.Matchers.is(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.ORIGINAL_OTP_KEY)).body("provisional_otp_key", org.hamcrest.Matchers.is(org.hamcrest.Matchers.notNullValue())).body("provisional_otp_key_created_at", org.hamcrest.Matchers.is(org.hamcrest.Matchers.notNullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnNotFoundIfUserNotFoundWhenProvisionNewOtpKey() {
        givenSetup().when().post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.USER_2FA_PROVISION_URL, "this is not a valid user external ID")).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    public void shouldActivateNewOtpKey() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.lang.String newOtpKey = givenSetup().when().post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.USER_2FA_PROVISION_URL, externalId)).then().statusCode(200).extract().body().jsonPath().get("provisional_otp_key");
        com.warrenstrange.googleauth.GoogleAuthenticator testAuthenticator = new com.warrenstrange.googleauth.GoogleAuthenticator();
        int passCode = testAuthenticator.getTotpPassword(newOtpKey);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(java.util.Map.of("second_factor", "APP", "code", passCode))).post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.USER_2FA_ACTIVATE_URL, externalId)).then().statusCode(200).body("username", org.hamcrest.Matchers.is(username)).body("second_factor", org.hamcrest.Matchers.is("APP")).body("otp_key", org.hamcrest.Matchers.is(newOtpKey)).body("provisional_otp_key", org.hamcrest.Matchers.is(org.hamcrest.CoreMatchers.nullValue())).body("provisional_otp_key_created_at", org.hamcrest.Matchers.is(org.hamcrest.CoreMatchers.nullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnBadRequestIfAttemptToActivateNewOtpKeyWithInvalidRequest() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.lang.String newOtpKey = givenSetup().when().post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.USER_2FA_PROVISION_URL, externalId)).then().statusCode(200).extract().body().jsonPath().get("provisional_otp_key");
        com.warrenstrange.googleauth.GoogleAuthenticator testAuthenticator = new com.warrenstrange.googleauth.GoogleAuthenticator();
        int passCode = testAuthenticator.getTotpPassword(newOtpKey);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(java.util.Map.of("second_factor", "NOT VALID", "code", passCode))).post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.USER_2FA_ACTIVATE_URL, externalId)).then().statusCode(400);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnUnauthorizedIfAttemptToActivateNewOtpKeyWithIncorrectCode() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.lang.String newOtpKey = givenSetup().when().post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.USER_2FA_PROVISION_URL, externalId)).then().statusCode(200).extract().body().jsonPath().get("provisional_otp_key");
        com.warrenstrange.googleauth.GoogleAuthenticator testAuthenticator = new com.warrenstrange.googleauth.GoogleAuthenticator();
        int passCode = testAuthenticator.getTotpPassword(newOtpKey);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(java.util.Map.of("second_factor", "APP", "code", passCode + 1))).post(java.lang.String.format(uk.gov.pay.adminusers.resources.UserResourceSecondFactorProvisioningIT.USER_2FA_ACTIVATE_URL, externalId)).then().statusCode(401);
    }
}
