package uk.gov.pay.adminusers.resources;
public class UserResourceUpdateServiceRoleIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    public void shouldUpdateUserServiceRole() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertAdmin();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        java.lang.String serviceExternalId = service.getExternalId();
        java.lang.String username1 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email1 = username1 + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service, role.getId()).withUsername(username1).withEmail(email1).insertUser();
        java.lang.String username2 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email2 = username2 + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service, role.getId()).withUsername(username2).withEmail(email2).insertUser();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("role_name", "view-and-refund"));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).put(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_SERVICE_RESOURCE, user.getExternalId(), serviceExternalId)).then().statusCode(200).body("username", org.hamcrest.core.Is.is(user.getUsername())).body("service_roles[0].role.name", org.hamcrest.core.Is.is("view-and-refund")).body("service_roles[0].role.description", org.hamcrest.core.Is.is("View and Refund"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError404_ifUserNotFound_whenUpdatingServiceRole() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService().getExternalId();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("role_name", "view-and-refund"));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).put(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_SERVICE_RESOURCE, "non-existent", serviceExternalId)).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    public void shouldError412_ifNoOfMinimumAdminsLimitReached_whenUpdatingServiceRole() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertAdmin();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        java.lang.String serviceExternalId = service.getExternalId();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service, role.getId()).withUsername(username).withEmail(email).insertUser();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("role_name", "view-and-refund"));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).put(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_SERVICE_RESOURCE, user.getExternalId(), serviceExternalId)).then().statusCode(412).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Service admin limit reached. At least 1 admin(s) required"));
    }
}
