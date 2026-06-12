package uk.gov.pay.adminusers.resources;
public class ServiceResourceIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private java.lang.String serviceExternalId;

    private uk.gov.pay.adminusers.model.User userWithRoleAdminInService1;

    private uk.gov.pay.adminusers.model.User user1WithRoleViewInService1;

    private uk.gov.pay.adminusers.model.Role roleView;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        uk.gov.pay.adminusers.model.Role roleAdmin = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertAdmin();
        roleView = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).withName("roleView").insertRole();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        serviceExternalId = service.getExternalId();
        java.lang.String username1 = "c" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email1 = username1 + "@example.com";
        userWithRoleAdminInService1 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service, roleAdmin.getId()).withUsername(username1).withEmail(email1).insertUser();
        java.lang.String username2 = "b" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email2 = username2 + "@example.com";
        user1WithRoleViewInService1 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service, roleView.getId()).withUsername(username2).withEmail(email2).insertUser();
        java.lang.String username3 = "a" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email3 = username3 + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service, roleView.getId()).withUsername(username3).withEmail(email3).insertUser();
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnListOfAllUsersWithRolesForAGivenServiceOrderedByUsername_identifiedByExternalid() {
        uk.gov.pay.adminusers.model.Role role1 = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).withName("role-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid()).insertRole();
        uk.gov.pay.adminusers.model.Role role2 = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).withName("role-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid()).insertRole();
        uk.gov.pay.adminusers.model.Service service1 = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withExperimentalFeaturesEnabled(true).insertService();
        uk.gov.pay.adminusers.model.Service service2 = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withExperimentalFeaturesEnabled(false).insertService();
        java.lang.String username1 = "zoe-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email1 = username1 + "@example.com";
        uk.gov.pay.adminusers.model.User user1 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username1).withEmail(email1).withServiceRole(service1.getId(), role1.getId()).insertUser();
        java.lang.String username2 = "tim-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email2 = username2 + "@example.com";
        uk.gov.pay.adminusers.model.User user2 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username2).withEmail(email2).withServiceRole(service1.getId(), role2.getId()).insertUser();
        java.lang.String username3 = "bob-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email3 = username3 + "@example.com";
        uk.gov.pay.adminusers.model.User user3 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username3).withEmail(email3).withServiceRole(service1.getId(), role2.getId()).insertUser();
        java.lang.String username4 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email4 = username4 + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username4).withEmail(email4).withServiceRole(service2.getId(), role1.getId()).insertUser();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", service1.getExternalId())).then().statusCode(200).body("$", org.hamcrest.Matchers.hasSize(3)).body("[0].username", org.hamcrest.core.Is.is(user3.getUsername())).body("[0]._links", org.hamcrest.Matchers.hasSize(1)).body("[0]._links[0].href", org.hamcrest.core.Is.is("http://localhost:8080/v1/api/users/" + user3.getExternalId())).body("[0]._links[0].method", org.hamcrest.core.Is.is("GET")).body("[0]._links[0].rel", org.hamcrest.core.Is.is("self")).body("[1].username", org.hamcrest.core.Is.is(user2.getUsername())).body("[1]._links", org.hamcrest.Matchers.hasSize(1)).body("[1]._links[0].href", org.hamcrest.core.Is.is("http://localhost:8080/v1/api/users/" + user2.getExternalId())).body("[1]._links[0].method", org.hamcrest.core.Is.is("GET")).body("[1]._links[0].rel", org.hamcrest.core.Is.is("self")).body("[2].username", org.hamcrest.core.Is.is(user1.getUsername())).body("[2]._links", org.hamcrest.Matchers.hasSize(1)).body("[2]._links[0].href", org.hamcrest.core.Is.is("http://localhost:8080/v1/api/users/" + user1.getExternalId())).body("[2]._links[0].method", org.hamcrest.core.Is.is("GET")).body("[2]._links[0].rel", org.hamcrest.core.Is.is("self"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnAGivenService_identifiedByExternalid() {
        uk.gov.pay.adminusers.model.Service service1 = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withExperimentalFeaturesEnabled(true).insertService();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/", service1.getExternalId())).then().statusCode(200).body("experimental_features_enabled", org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void getServiceUsers_shouldReturn404WhenServiceDoesNotExist() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get("/v1/api/services/999/users").then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    public void removeServiceUser_shouldRemoveAnUserFromAService() {
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> serviceRoleForUserBefore = databaseHelper.findServiceRoleForUser(user1WithRoleViewInService1.getId());
        org.hamcrest.MatcherAssert.assertThat(serviceRoleForUserBefore.size(), org.hamcrest.core.Is.is(1));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", serviceExternalId)).then().body("$", org.hamcrest.core.IsIterableContaining.hasItem(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasEntry("username", user1WithRoleViewInService1.getUsername()))));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).header(uk.gov.pay.adminusers.resources.ServiceResource.HEADER_USER_CONTEXT, userWithRoleAdminInService1.getExternalId()).delete(java.lang.String.format("/v1/api/services/%s/users/%s", serviceExternalId, user1WithRoleViewInService1.getExternalId())).then().statusCode(204).body(org.hamcrest.Matchers.emptyString());
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> serviceRoleForUserAfter = databaseHelper.findServiceRoleForUser(user1WithRoleViewInService1.getId());
        org.hamcrest.MatcherAssert.assertThat(serviceRoleForUserAfter.isEmpty(), org.hamcrest.core.Is.is(true));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", serviceExternalId)).then().body("$", org.hamcrest.Matchers.not(org.hamcrest.core.IsIterableContaining.hasItem(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasEntry("username", user1WithRoleViewInService1.getUsername())))));
    }

    @org.junit.jupiter.api.Test
    public void remove_should_remove_user_from_specified_service_only() {
        uk.gov.pay.adminusers.model.Service anotherService = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        databaseHelper.addUserServiceRole(user1WithRoleViewInService1.getId(), anotherService.getId(), roleView.getId());
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", serviceExternalId)).then().body("$", org.hamcrest.core.IsIterableContaining.hasItem(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasEntry("username", user1WithRoleViewInService1.getUsername()))));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", anotherService.getExternalId())).then().body("$", org.hamcrest.core.IsIterableContaining.hasItem(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasEntry("username", user1WithRoleViewInService1.getUsername()))));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).header(uk.gov.pay.adminusers.resources.ServiceResource.HEADER_USER_CONTEXT, userWithRoleAdminInService1.getExternalId()).delete(java.lang.String.format("/v1/api/services/%s/users/%s", serviceExternalId, user1WithRoleViewInService1.getExternalId())).then().statusCode(204).body(org.hamcrest.Matchers.emptyString());
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", serviceExternalId)).then().body("$", org.hamcrest.Matchers.not(org.hamcrest.core.IsIterableContaining.hasItem(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasEntry("username", user1WithRoleViewInService1.getUsername())))));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/users", anotherService.getExternalId())).then().body("$", org.hamcrest.core.IsIterableContaining.hasItem(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasEntry("username", user1WithRoleViewInService1.getUsername()))));
    }

    @org.junit.jupiter.api.Test
    public void removeServiceUser_shouldNotBeAbleToRemoveAnUserItself() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).header(uk.gov.pay.adminusers.resources.ServiceResource.HEADER_USER_CONTEXT, userWithRoleAdminInService1.getExternalId()).delete(java.lang.String.format("/v1/api/services/%s/users/%s", serviceExternalId, userWithRoleAdminInService1.getExternalId())).then().statusCode(409).body(org.hamcrest.Matchers.emptyString());
    }

    @org.junit.jupiter.api.Test
    public void removeServiceUser_shouldReturnForbiddenWhenRemoverIsMissing() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).header(uk.gov.pay.adminusers.resources.ServiceResource.HEADER_USER_CONTEXT, " ").delete(java.lang.String.format("/v1/api/services/%s/users/%s", serviceExternalId, userWithRoleAdminInService1.getExternalId())).then().statusCode(403).body(org.hamcrest.Matchers.emptyString());
    }

    @org.junit.jupiter.api.Test
    public void removeServiceUser_shouldReturnForbiddenWhenUserContextHeaderIsMissing() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).delete(java.lang.String.format("/v1/api/services/%s/users/%s", serviceExternalId, userWithRoleAdminInService1.getExternalId())).then().statusCode(403).body(org.hamcrest.Matchers.emptyString());
    }
}
