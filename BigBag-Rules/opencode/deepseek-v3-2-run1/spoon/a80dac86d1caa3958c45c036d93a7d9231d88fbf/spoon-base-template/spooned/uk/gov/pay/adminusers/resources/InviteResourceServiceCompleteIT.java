package uk.gov.pay.adminusers.resources;
public class InviteResourceServiceCompleteIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    public static final java.lang.String INVITES_RESOURCE_URL = "/v1/api/invites";

    @org.junit.jupiter.api.Test
    public void shouldReturn200withDisabledInviteLinkingToCreatedUser_WhenPassedAValidInviteCode_withoutGatewayAccountIds() {
        java.lang.String email = java.lang.String.format("%s@example.gov.uk", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        java.lang.String telephoneNumber = "+447700900000";
        java.lang.String password = "valid_password";
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withTelephoneNumber(telephoneNumber).withEmail(email).withPassword(password).insertServiceInvite();
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceServiceCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode()).body("invite._links", org.hamcrest.Matchers.hasSize(1)).body("invite._links[0].href", org.hamcrest.text.MatchesPattern.matchesPattern("^http://localhost:8080/v1/api/users/[0-9a-z]{32}$")).body("invite._links[0].rel", org.hamcrest.core.Is.is("user")).body("invite.disabled", org.hamcrest.core.Is.is(true)).body("user_external_id", org.hamcrest.text.MatchesPattern.matchesPattern("[0-9a-z]{32}$")).body("service_external_id", org.hamcrest.text.MatchesPattern.matchesPattern("[0-9a-z]{32}$"));
        java.util.Map<java.lang.String, java.lang.Object> createdUser = databaseHelper.findUserByUsername(email).stream().findFirst().get();
        java.util.Map<java.lang.String, java.lang.Object> role = databaseHelper.findServiceRoleForUser(((java.lang.Integer) (createdUser.get("id")))).stream().findFirst().get();
        java.util.Map<java.lang.String, java.lang.Object> invite = databaseHelper.findInviteByCode(inviteCode).stream().findFirst().get();
        org.hamcrest.MatcherAssert.assertThat(role.get("id"), org.hamcrest.core.Is.is(invite.get("role_id")));
        org.hamcrest.MatcherAssert.assertThat(createdUser.get("password"), org.hamcrest.core.Is.is(password));
        org.hamcrest.MatcherAssert.assertThat(createdUser.get("email"), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(createdUser.get("telephone_number"), org.hamcrest.core.Is.is(telephoneNumber));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn200withDisabledInviteLinkingToCreatedUser_WhenPassedAValidInviteCode_withGatewayAccountIds() throws java.lang.Exception {
        java.lang.String email = java.lang.String.format("%s@example.gov.uk", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        java.lang.String telephoneNumber = "+447700900000";
        java.lang.String password = "valid_password";
        java.lang.String gatewayAccountId1 = java.lang.String.valueOf(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt());
        java.lang.String gatewayAccountId2 = java.lang.String.valueOf(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt());
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> payload = java.util.Map.of("gateway_account_ids", java.util.Arrays.asList(gatewayAccountId1, gatewayAccountId2));
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withTelephoneNumber(telephoneNumber).withEmail(email).withPassword(password).insertServiceInvite();
        io.restassured.response.ValidatableResponse validatableResponse = givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).post(((uk.gov.pay.adminusers.resources.InviteResourceServiceCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        validatableResponse.body("invite._links", org.hamcrest.Matchers.hasSize(1)).body("invite._links[0].href", org.hamcrest.text.MatchesPattern.matchesPattern("^http://localhost:8080/v1/api/users/[0-9a-z]{32}$")).body("invite._links[0].rel", org.hamcrest.core.Is.is("user")).body("invite.disabled", org.hamcrest.core.Is.is(true)).body("user_external_id", org.hamcrest.text.MatchesPattern.matchesPattern("[0-9a-z]{32}$")).body("service_external_id", org.hamcrest.text.MatchesPattern.matchesPattern("[0-9a-z]{32}$"));
        java.lang.String userExternalId = validatableResponse.extract().path("user_external_id");
        java.lang.String serviceExternalId = validatableResponse.extract().path("service_external_id");
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, userExternalId)).then().statusCode(200).body("external_id", org.hamcrest.core.Is.is(userExternalId)).body("service_roles[0].service.external_id", org.hamcrest.core.Is.is(serviceExternalId)).body("service_roles[0].service.gateway_account_ids", org.hamcrest.Matchers.hasItems(gatewayAccountId1, gatewayAccountId2));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn410_WhenSameInviteCodeCompletedTwice() {
        java.lang.String email = java.lang.String.format("%s@example.gov.uk", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        java.lang.String telephoneNumber = "+447700900000";
        java.lang.String password = "valid_password";
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withTelephoneNumber(telephoneNumber).withEmail(email).withPassword(password).insertServiceInvite();
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceServiceCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.OK.getStatusCode());
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceServiceCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.GONE.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn409_ifAUserExistsWithTheSameEmail_whenServiceInviteCompletes() {
        java.lang.String email = java.lang.String.format("%s@example.gov.uk", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        java.lang.String username = email;
        java.lang.String telephoneNumber = "+447700900000";
        java.lang.String password = "valid_password";
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withTelephoneNumber(telephoneNumber).withEmail(email).withPassword(password).insertServiceInvite();
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser();
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceServiceCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn410_WheninviteIsDisabled() {
        java.lang.String email = java.lang.String.format("%s@example.gov.uk", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        java.lang.String telephoneNumber = "+447700900000";
        java.lang.String password = "valid_password";
        java.lang.String inviteCode = uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withTelephoneNumber(telephoneNumber).withEmail(email).withPassword(password).disabled().insertServiceInvite();
        givenSetup().when().post(((uk.gov.pay.adminusers.resources.InviteResourceServiceCompleteIT.INVITES_RESOURCE_URL + "/") + inviteCode) + "/complete").then().statusCode(javax.ws.rs.core.Response.Status.GONE.getStatusCode());
    }
}
