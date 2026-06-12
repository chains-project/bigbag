package uk.gov.pay.adminusers.resources;
public class InviteResourceOtpIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private java.lang.String code;

    private static final java.lang.String OTP_KEY = uk.gov.pay.adminusers.app.util.RandomIdGenerator.newId();

    private static final int PASSCODE = new com.warrenstrange.googleauth.GoogleAuthenticator().getTotpPassword(com.google.common.io.BaseEncoding.base32().encode(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY.getBytes()));

    private static final java.lang.String EMAIL = ("invited-" + org.apache.commons.lang3.RandomStringUtils.random(5)) + "@example.com";

    private static final java.lang.String TELEPHONE_NUMBER = "+447999999999";

    private static final java.lang.String PASSWORD = "a-secure-password";

    @org.junit.jupiter.api.BeforeEach
    public void givenAnExistingInvite() {
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY).expired().insertInvite();
    }

    @org.junit.jupiter.api.Test
    public void validateOtp_shouldCreateUserWhenValidOtp() throws java.lang.Exception {
        // create an invitation
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY).withTelephoneNumber(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.TELEPHONE_NUMBER).withPassword(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.PASSWORD).insertInvite();
        // generate valid invitationOtpRequest and execute it
        java.util.Map<java.lang.Object, java.lang.Object> invitationOtpRequest = java.util.Map.of("code", code, "otp", uk.gov.pay.adminusers.resources.InviteResourceOtpIT.PASSCODE);
        org.hamcrest.MatcherAssert.assertThat(databaseHelper.findInviteByCode(code).size(), org.hamcrest.core.Is.is(1));
        io.restassured.response.ValidatableResponse response = givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationOtpRequest)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_VALIDATE_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.CREATED.getStatusCode());
        java.lang.String externalId = response.extract().path("external_id");
        // check the response
        response.statusCode(201).body("id", org.hamcrest.Matchers.nullValue()).body("external_id", org.hamcrest.core.Is.is(externalId)).body("username", org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL)).body("password", org.hamcrest.Matchers.nullValue()).body("email", org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL)).body("telephone_number", org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.TELEPHONE_NUMBER)).body("otp_key", org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY)).body("login_counter", org.hamcrest.core.Is.is(0)).body("disabled", org.hamcrest.core.Is.is(false)).body("_links", org.hamcrest.Matchers.hasSize(1)).body("_links[0].href", org.hamcrest.core.Is.is("http://localhost:8080/v1/api/users/" + externalId)).body("_links[0].method", org.hamcrest.core.Is.is("GET")).body("_links[0].rel", org.hamcrest.core.Is.is("self"));
        // check if the user has been created and it is not disabled in the database
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> users = databaseHelper.findUserByUsername(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL);
        org.hamcrest.MatcherAssert.assertThat(users.size(), org.hamcrest.core.Is.is(1));
        java.util.Map<java.lang.String, java.lang.Object> createdUser = users.get(0);
        org.hamcrest.MatcherAssert.assertThat(createdUser.get("username"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL));
        org.hamcrest.MatcherAssert.assertThat(createdUser.get("email"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL));
        org.hamcrest.MatcherAssert.assertThat(createdUser.get("password"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.PASSWORD));
        org.hamcrest.MatcherAssert.assertThat(createdUser.get("disabled"), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void validateOtp_shouldFail_whenInvalidCode() throws java.lang.Exception {
        java.util.Map<java.lang.Object, java.lang.Object> invitationOtpRequest = java.util.Map.of("code", "non-existent-code", "otp", uk.gov.pay.adminusers.resources.InviteResourceOtpIT.PASSCODE);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationOtpRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_VALIDATE_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void validateOtp_shouldFail_whenInvalidOtpAuthCode() throws java.lang.Exception {
        // create an invitation
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY).withTelephoneNumber(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.TELEPHONE_NUMBER).withPassword(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.PASSWORD).insertInvite();
        // generate invalid invitationOtpRequest and execute it
        java.util.Map<java.lang.Object, java.lang.Object> invitationOtpRequest = java.util.Map.of("code", code, "otp", 123456);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationOtpRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_VALIDATE_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.UNAUTHORIZED.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void validateOtp_shouldFailAndLockInvite_whenInvalidOtpAuthCode_ifMaxRetryExceeded() throws java.lang.Exception {
        // create an invitation
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY).withTelephoneNumber(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.TELEPHONE_NUMBER).withPassword(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.PASSWORD).withLoginCounter(9).insertInvite();
        // generate invalid invitationOtpRequest and execute it
        java.util.Map<java.lang.Object, java.lang.Object> invitationOtpRequest = java.util.Map.of("code", code, "otp", 123456);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationOtpRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_VALIDATE_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.GONE.getStatusCode());
        // check if "login_counter" and "disabled" columns are properly updated
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> foundInvites = databaseHelper.findInviteByCode(code);
        org.hamcrest.MatcherAssert.assertThat(foundInvites.size(), org.hamcrest.core.Is.is(1));
        java.util.Map<java.lang.String, java.lang.Object> foundInvite = foundInvites.get(0);
        org.hamcrest.MatcherAssert.assertThat(foundInvite.get("disabled"), org.hamcrest.core.Is.is(java.lang.Boolean.TRUE));
        org.hamcrest.MatcherAssert.assertThat(foundInvite.get("login_counter"), org.hamcrest.core.Is.is(10));
    }

    @org.junit.jupiter.api.Test
    public void validateOtp_shouldFail_whenAllMandatoryFieldsAreMissing() throws java.lang.Exception {
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(java.util.Collections.emptyMap())).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_VALIDATE_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void resendOtp_shouldUpdateTelephoneNumber_whenValidOtp() throws java.lang.Exception {
        // create an invitation with initial telephone number
        java.lang.String initialTelephoneNumber = "+447451111111";
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.EMAIL).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY).withTelephoneNumber(initialTelephoneNumber).withPassword(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.PASSWORD).expired().insertInvite();
        // generate new invitationOtpRequest with new telephone number
        java.lang.String newTelephoneNumber = "+447452222222";
        java.util.Map<java.lang.Object, java.lang.Object> resendRequest = java.util.Map.of("code", code, "telephone_number", newTelephoneNumber);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(resendRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_RESEND_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        // check if we are using the newTelephoneNumber in the invitation
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> foundInvites = databaseHelper.findInviteByCode(code);
        org.hamcrest.MatcherAssert.assertThat(foundInvites.size(), org.hamcrest.core.Is.is(1));
        java.util.Map<java.lang.String, java.lang.Object> foundInvite = foundInvites.get(0);
        org.hamcrest.MatcherAssert.assertThat(foundInvite.get("telephone_number"), org.hamcrest.core.Is.is(newTelephoneNumber));
    }

    @org.junit.jupiter.api.Test
    public void resendOtp_shouldFail_whenAllMandatoryFieldsAreMissing() throws java.lang.Exception {
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(java.util.Collections.emptyMap())).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_RESEND_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void validateServiceOtpKey_shouldSucceed_whenValidOtp() throws java.lang.Exception {
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY).insertInvite();
        java.util.Map<java.lang.Object, java.lang.Object> sendRequest = java.util.Map.of("code", code, "otp", uk.gov.pay.adminusers.resources.InviteResourceOtpIT.PASSCODE);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(sendRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_INVITES_VALIDATE_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void validateServiceOtpKey_shouldFailWith401_whenInvalidOtp() throws java.lang.Exception {
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceOtpIT.OTP_KEY).insertInvite();
        int invalidOtp = 111111;
        java.util.Map<java.lang.Object, java.lang.Object> sendRequest = java.util.Map.of("code", code, "otp", invalidOtp);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(sendRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_INVITES_VALIDATE_OTP_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.UNAUTHORIZED.getStatusCode());
    }
}
