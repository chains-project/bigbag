package uk.gov.pay.adminusers.resources;
class UserResourceCreateServiceRoleIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    void shouldSuccess_whenAddServiceRoleForUser() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertAdmin();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("service_external_id", service.getExternalId(), "role_name", role.getName()));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_SERVICES_RESOURCE, user.getExternalId())).then().statusCode(200).body("username", org.hamcrest.core.Is.is(user.getUsername())).body("service_roles", org.hamcrest.collection.IsCollectionWithSize.hasSize(1)).body("service_roles[0].role.name", org.hamcrest.core.Is.is(role.getName())).body("service_roles[0].service.external_id", org.hamcrest.core.Is.is(service.getExternalId()));
    }

    @org.junit.jupiter.api.Test
    void shouldError_whenAddServiceRoleForUser_ifMandatoryParamsMissing() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertAdmin();
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("role_name", role.getName()));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_SERVICES_RESOURCE, user.getExternalId())).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Field [service_external_id] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldError_whenAddServiceRoleForUser_ifUserAlreadyHasService() {
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertAdmin();
        java.lang.String roleName = "view-and-refund";
        uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).withName(roleName).insertAdmin();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service.getId(), role.getId()).withUsername(username).withEmail(email).insertUser();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("service_external_id", service.getExternalId(), "role_name", roleName));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_SERVICES_RESOURCE, user.getExternalId())).then().statusCode(409).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is(java.lang.String.format("Cannot assign service role. user [%s] already got access to service [%s].", user.getExternalId(), service.getExternalId())));
    }
}
