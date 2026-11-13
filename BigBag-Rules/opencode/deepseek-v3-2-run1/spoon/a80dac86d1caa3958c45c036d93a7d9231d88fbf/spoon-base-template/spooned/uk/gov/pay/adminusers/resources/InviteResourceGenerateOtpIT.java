package uk.gov.pay.adminusers.resources;
public class InviteResourceGenerateOtpIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private java.lang.String code;

    private static final java.lang.String OTP_KEY = uk.gov.pay.adminusers.app.util.RandomIdGenerator.newId();

    private static final java.lang.String EMAIL = ("invited-" + org.apache.commons.lang3.RandomStringUtils.random(5)) + "@example.com";

    private static final java.lang.String TELEPHONE_NUMBER = "+447999999999";

    private static final java.lang.String PASSWORD = "a-secure-password";

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldSucceed_forUserInvite_evenWhenTokenIsExpired_sinceItShouldBeValidatedOnGetInvite() throws java.lang.Exception {
        givenAnExistingUserInvite();
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("telephone_number", uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.TELEPHONE_NUMBER, "password", uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.PASSWORD);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_GENERATE_OTP_RESOURCE_URL, code)).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_should_FailforUserInvite_whenInviteDoesNotExist() throws java.lang.Exception {
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("telephone_number", uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.TELEPHONE_NUMBER, "password", uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.PASSWORD);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_GENERATE_OTP_RESOURCE_URL, "not-existing-code")).then().statusCode(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldFail_forUserInvite_whenAllMandatoryFieldsAreMissing() throws java.lang.Exception {
        givenAnExistingUserInvite();
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Collections.emptyMap();
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_GENERATE_OTP_RESOURCE_URL, code)).then().statusCode(javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldSucceed_forServiceInvite_evenWhenTokenIsExpired_sinceItShouldBeValidatedOnGetInvite() {
        givenAnExistingServiceInvite();
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_GENERATE_OTP_RESOURCE_URL, code)).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldSucceed_forServiceInvite_whenNoFieldsPresent() throws java.lang.Exception {
        givenAnExistingServiceInvite();
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Collections.emptyMap();
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_GENERATE_OTP_RESOURCE_URL, code)).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void generateOtp_shouldSucceed_forServiceInvite_whenPasswordAndPhoneNumberPresent() throws java.lang.Exception {
        givenAnExistingServiceInvite();
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("telephone_number", uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.TELEPHONE_NUMBER, "password", uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.PASSWORD);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_GENERATE_OTP_RESOURCE_URL, code)).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    private void givenAnExistingUserInvite() {
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.EMAIL).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.OTP_KEY).expired().insertInvite();
    }

    private void givenAnExistingServiceInvite() {
        code = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.EMAIL).withOtpKey(uk.gov.pay.adminusers.resources.InviteResourceGenerateOtpIT.OTP_KEY).expired().insertServiceInvite();
    }
}
