package uk.gov.pay.adminusers.resources;
public class InviteResourceUserCompleteIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    public static final java.lang.String INVITES_RESOURCE_URL = "/v1/api/invites";

    @org.junit.jupiter.api.Test
    public void shouldReturn200WithDisabledInvite_whenExistingUserSubscribingToAnExistingService() {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = java.lang.String.format("%s@example.gov.uk", username);
        java.lang.String telephoneNumber = "+447700900000";
        java.lang.String password = "valid_password";
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String userExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withExternalId(userExternalId).withEmail(email).withUsername(username).insertUser();
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withServiceExternalId(serviceExternalId).withTelephoneNumber(telephoneNumber).withEmail(email).withPassword(password).insertInvite();
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceUserCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode()).body("invite.disabled", org.hamcrest.core.Is.is(true)).body("service_external_id", org.hamcrest.core.Is.is(serviceExternalId)).body("user_external_id", org.hamcrest.core.Is.is(userExternalId));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, userExternalId)).then().statusCode(200).body("service_roles", org.hamcrest.Matchers.hasSize(1)).body("service_roles[0].service.external_id", org.hamcrest.core.Is.is(serviceExternalId));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn404_whenInviteCodeNotFound() {
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceUserCompleteIT.INVITES_RESOURCE_URL + "/") + "non-existent-code") + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn410_withDisabledInvite() {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = java.lang.String.format("%s@example.gov.uk", username);
        java.lang.String telephoneNumber = "+447700900000";
        java.lang.String password = "valid_password";
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String userExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withExternalId(userExternalId).withEmail(email).withUsername(username).insertUser();
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withServiceExternalId(serviceExternalId).withTelephoneNumber(telephoneNumber).withEmail(email).withPassword(password).insertInvite();
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceUserCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceUserCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.GONE.getStatusCode());
    }
}
