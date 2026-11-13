package uk.gov.pay.adminusers.resources;
public class ToolboxEndpointResourceIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private uk.gov.pay.adminusers.model.User user;

    private uk.gov.pay.adminusers.model.Role role;

    private java.lang.String serviceExternalId;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).withName("roleView").insertRole();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        serviceExternalId = service.getExternalId();
        java.lang.String username = "b" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service, role.getId()).withUsername(username).withEmail(email).insertUser();
    }

    @org.junit.jupiter.api.Test
    public void should_remove_user_from_service() {
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> serviceRoleForUserBefore = databaseHelper.findServiceRoleForUser(user.getId());
        org.hamcrest.MatcherAssert.assertThat(serviceRoleForUserBefore.size(), org.hamcrest.core.Is.is(1));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", serviceExternalId)).then().body("$", org.hamcrest.core.IsIterableContaining.hasItem(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasEntry("username", user.getUsername()))));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).delete(java.lang.String.format("/v1/api/toolbox/services/%s/users/%s", serviceExternalId, user.getExternalId())).then().statusCode(204).body(org.hamcrest.Matchers.emptyString());
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> serviceRoleForUserAfter = databaseHelper.findServiceRoleForUser(user.getId());
        org.hamcrest.MatcherAssert.assertThat(serviceRoleForUserAfter.isEmpty(), org.hamcrest.core.Is.is(true));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", serviceExternalId)).then().body("$", org.hamcrest.Matchers.not(org.hamcrest.core.IsIterableContaining.hasItem(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasEntry("username", user.getUsername())))));
    }
}
