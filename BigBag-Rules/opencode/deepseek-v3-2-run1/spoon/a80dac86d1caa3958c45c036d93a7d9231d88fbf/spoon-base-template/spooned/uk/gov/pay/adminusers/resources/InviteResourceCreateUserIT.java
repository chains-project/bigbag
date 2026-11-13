package uk.gov.pay.adminusers.resources;
public class InviteResourceCreateUserIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private uk.gov.pay.adminusers.model.Service service;

    private java.lang.String roleAdminName;

    private java.lang.String senderExternalId;

    @org.junit.jupiter.api.BeforeEach
    public void givenAnExistingServiceAndARole() {
        service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        roleAdminName = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertAdmin().getName();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        senderExternalId = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service.getId(), uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId()).withUsername(username).withEmail(email).insertUser().getExternalId();
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldSucceed_whenInvitingANewUser() throws java.lang.Exception {
        java.lang.String email = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(5) + "-invite@example.com";
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("sender", senderExternalId, "email", email, "role_name", roleAdminName, "service_external_id", service.getExternalId());
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITE_USER_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.CREATED.getStatusCode()).body("email", org.hamcrest.core.Is.is(email.toLowerCase(java.util.Locale.ENGLISH))).body("telephone_number", org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue())).body("_links", org.hamcrest.Matchers.hasSize(1)).body("_links[0].href", org.hamcrest.text.MatchesPattern.matchesPattern("^https://selfservice.pymnt.localdomain/invites/[0-9a-z]{32}$")).body("_links[0].method", org.hamcrest.core.Is.is("GET")).body("_links[0].rel", org.hamcrest.core.Is.is("invite"));
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldFail_whenAnInviteWithTheGivenEmailAlreadyExists() throws java.lang.Exception {
        java.lang.String existingUserEmail = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(5) + "-invite@example.com";
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(existingUserEmail).withServiceExternalId(serviceExternalId).insertInvite();
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("sender", senderExternalId, "email", existingUserEmail, "role_name", roleAdminName, "service_external_id", serviceExternalId);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITE_USER_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode()).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors", org.hamcrest.Matchers.hasItems(java.lang.String.format("invite with email [%s] already exists", existingUserEmail)));
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldFail_ifUserAlreadyBelongToService() throws java.lang.Exception {
        java.lang.String existingUserUsername = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String existingUserEmail = existingUserUsername + "-invite@example.com";
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.Integer serviceId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt();
        uk.gov.pay.adminusers.fixtures.InviteDbFixture.inviteDbFixture(databaseHelper).withEmail(existingUserEmail).withServiceExternalId(serviceExternalId).withServiceId(serviceId).insertInvite();
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(existingUserUsername).withEmail(existingUserEmail).withServiceRole(uk.gov.pay.adminusers.model.Service.from(serviceId, serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName("service name")), 2).insertUser();
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("sender", senderExternalId, "email", existingUserEmail, "role_name", roleAdminName, "service_external_id", serviceExternalId);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITE_USER_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.PRECONDITION_FAILED.getStatusCode()).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors", org.hamcrest.Matchers.hasItems(java.lang.String.format("user [%s] already in service [%s]", user.getExternalId(), serviceExternalId)));
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldFail_whenServiceDoesNotExist() throws java.lang.Exception {
        java.lang.String nonExistentServiceId = "non existant service external id";
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("sender", senderExternalId, "email", org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(5) + "-invite@example.com", "role_name", roleAdminName, "service_external_id", nonExistentServiceId);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITE_USER_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode()).body(org.hamcrest.Matchers.emptyString());
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldFail_whenRoleDoesNotExist() throws java.lang.Exception {
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("sender", senderExternalId, "email", org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(5) + "-invite@example.com", "role_name", "non-existing-role", "service_external_id", service.getExternalId());
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITE_USER_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode()).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors", org.hamcrest.Matchers.hasItems("role [non-existing-role] not recognised"));
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldFail_whenSenderDoesNotExist() throws java.lang.Exception {
        java.lang.String email = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(5) + "-invite@example.com";
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("sender", "does-not-exist", "email", email, "role_name", roleAdminName, "service_external_id", service.getExternalId());
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITE_USER_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.FORBIDDEN.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldFail_whenSenderDoesNotHaveAdminRole() throws java.lang.Exception {
        int otherRoleId = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertRole().getId();
        java.lang.String senderUsername = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String senderEmail = senderUsername + "@example.com";
        java.lang.String senderWithNoAdminRole = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service.getId(), otherRoleId).withUsername(senderUsername).withEmail(senderEmail).insertUser().getExternalId();
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("sender", senderWithNoAdminRole, "email", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid() + "-invite@example.com", "role_name", roleAdminName, "service_external_id", service.getExternalId());
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITE_USER_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.FORBIDDEN.getStatusCode());
    }

    @org.junit.jupiter.api.Test
    public void createInvitation_shouldFail_whenSenderDoesNotBelongToTheGivenService() throws java.lang.Exception {
        uk.gov.pay.adminusers.model.Service otherService = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        java.lang.String senderUsername = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String senderEmail = senderUsername + "@example.com";
        java.lang.String senderExternalId = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(otherService.getId(), uk.gov.pay.adminusers.persistence.entity.Role.ADMIN.getId()).withUsername(senderUsername).withEmail(senderEmail).insertUser().getExternalId();
        java.util.Map<java.lang.Object, java.lang.Object> invitationRequest = java.util.Map.of("sender", senderExternalId, "email", uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid() + "-invite@example.com", "role_name", roleAdminName, "service_external_id", service.getExternalId());
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invitationRequest)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.INVITE_USER_RESOURCE_URL).then().statusCode(javax.ws.rs.core.Response.Status.FORBIDDEN.getStatusCode());
    }
}
