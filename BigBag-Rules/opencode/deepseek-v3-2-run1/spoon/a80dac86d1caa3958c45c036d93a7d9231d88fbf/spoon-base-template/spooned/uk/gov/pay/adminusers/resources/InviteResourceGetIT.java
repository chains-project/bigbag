package uk.gov.pay.adminusers.resources;
public class InviteResourceGetIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    public void getInvitation_shouldSucceed() {
        java.lang.String email = "user@example.com";
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(email).insertInvite();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get((uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_RESOURCE_URL + "/") + inviteCode).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode()).body("email", org.hamcrest.core.Is.is(email)).body("telephone_number", org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue())).body("disabled", org.hamcrest.core.Is.is(false)).body("user_exist", org.hamcrest.core.Is.is(false)).body("attempt_counter", org.hamcrest.core.Is.is(0)).body("password_set", org.hamcrest.core.Is.is(false));
    }

    /**
     * This situation happens when OTP is generated (with telephone_number) and then the GET Invite is again requested
     *  (still non-expired invite link)
     */
    @org.junit.jupiter.api.Test
    public void getInvitation_shouldSucceedWithTelephoneNumber_whenIsAvailable() {
        java.lang.String email = "user@example.com";
        java.lang.String telephoneNumber = "+440787654534";
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withTelephoneNumber(telephoneNumber).withEmail(email).insertInvite();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get((uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_RESOURCE_URL + "/") + inviteCode).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode()).body("email", org.hamcrest.core.Is.is(email)).body("telephone_number", org.hamcrest.core.Is.is(telephoneNumber)).body("disabled", org.hamcrest.core.Is.is(false)).body("attempt_counter", org.hamcrest.core.Is.is(0));
    }

    @org.junit.jupiter.api.Test
    public void getInvitation_shouldFail_whenExpired() {
        java.lang.String expiredCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).expired().insertInvite();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get((uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_RESOURCE_URL + "/") + expiredCode).then().statusCode(javax.ws.rs.core.Response.Status.GONE.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void getInvitation_shouldFail_whenDisabled() {
        java.lang.String expiredCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).disabled().insertInvite();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get((uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_RESOURCE_URL + "/") + expiredCode).then().statusCode(javax.ws.rs.core.Response.Status.GONE.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldFail_whenInvalidCode() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_RESOURCE_URL + "/fake-code").then().statusCode(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void getInvitations_shouldSucceed() {
        java.lang.String serviceExternalId = "sdfgsdgytgkh";
        java.lang.String email = "user@example.com";
        uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(email).withServiceExternalId(serviceExternalId).insertInvite();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get((uk.gov.pay.adminusers.resources.IntegrationTest.INVITES_RESOURCE_URL + "?serviceId=") + serviceExternalId).then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode()).body("[0].email", org.hamcrest.core.Is.is(email)).body("[0].telephone_number", org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue())).body("[0].disabled", org.hamcrest.core.Is.is(false)).body("[0].expired", org.hamcrest.core.Is.is(false)).body("[0].user_exist", org.hamcrest.core.Is.is(false)).body("[0].attempt_counter", org.hamcrest.core.Is.is(0)).body("[0].password_set", org.hamcrest.core.Is.is(false));
    }
}
