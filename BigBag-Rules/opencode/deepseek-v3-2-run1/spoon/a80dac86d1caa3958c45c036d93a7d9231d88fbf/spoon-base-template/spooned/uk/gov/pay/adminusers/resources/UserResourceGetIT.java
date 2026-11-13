package uk.gov.pay.adminusers.resources;
class UserResourceGetIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    void should_return_empty_map_when_getting_admin_emails_for_gateway_accounts() throws java.lang.Exception {
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(java.util.Map.of("gatewayAccountIds", java.util.List.of("gatewayAccount1")))).post("/v1/api/users/admin-emails-for-gateway-accounts").then().statusCode(200).body("gatewayAccount1", org.hamcrest.Matchers.hasSize(0));
    }

    @org.junit.jupiter.api.Test
    void should_return_admin_emails_for_gateway_accounts() throws java.lang.Exception {
        java.lang.String gatewayAccount1 = java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt());
        java.lang.String gatewayAccount2 = java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt());
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withGatewayAccountIds(gatewayAccount1, gatewayAccount2).insertService();
        uk.gov.pay.adminusers.model.Role adminRole = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertAdmin();
        var adminUser1 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service.getId(), adminRole.getId()).insertUser();
        var adminUser2 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service.getId(), adminRole.getId()).insertUser();
        uk.gov.pay.adminusers.model.Role viewOnlyRole = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).withName("view-only").insertRole();
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service.getId(), viewOnlyRole.getId()).insertUser();
        var gatewayAccountIds = java.util.Map.of("gatewayAccountIds", java.util.List.of(gatewayAccount1, gatewayAccount2));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(gatewayAccountIds)).post("/v1/api/users/admin-emails-for-gateway-accounts").then().statusCode(200).body(gatewayAccount1, org.hamcrest.Matchers.hasSize(2)).body(gatewayAccount1, org.hamcrest.Matchers.hasItems(adminUser1.getEmail(), adminUser2.getEmail())).body(gatewayAccount2, org.hamcrest.Matchers.hasSize(2)).body(gatewayAccount2, org.hamcrest.Matchers.hasItems(adminUser1.getEmail(), adminUser2.getEmail()));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUser_whenGetUserWithExternalId() {
        java.lang.String gatewayAccount1 = java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt());
        java.lang.String gatewayAccount2 = java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt());
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withGatewayAccountIds(gatewayAccount1, gatewayAccount2).insertService();
        java.lang.String serviceExternalId = service.getExternalId();
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture(databaseHelper).insertRole();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service.getId(), role.getId()).withUsername(username).withEmail(email).insertUser();
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, user.getExternalId())).then().statusCode(200).body("external_id", org.hamcrest.core.Is.is(user.getExternalId())).body("username", org.hamcrest.core.Is.is(user.getUsername())).body("password", org.hamcrest.Matchers.nullValue()).body("email", org.hamcrest.core.Is.is(user.getEmail())).body("service_roles", org.hamcrest.Matchers.hasSize(1)).body("service_roles[0].service.external_id", org.hamcrest.core.Is.is(serviceExternalId)).body("service_roles[0].service.name", org.hamcrest.core.Is.is(service.getName())).body("telephone_number", org.hamcrest.core.Is.is(user.getTelephoneNumber())).body("otp_key", org.hamcrest.core.Is.is(user.getOtpKey())).body("login_counter", org.hamcrest.core.Is.is(0)).body("disabled", org.hamcrest.core.Is.is(false)).body("service_roles[0].role.name", org.hamcrest.core.Is.is(role.getName())).body("service_roles[0].role.description", org.hamcrest.core.Is.is(role.getDescription())).body("service_roles[0].role.permissions", org.hamcrest.Matchers.hasSize(role.getPermissions().size())).body("_links", org.hamcrest.Matchers.hasSize(1)).body("_links[0].href", org.hamcrest.core.Is.is("http://localhost:8080/v1/api/users/" + user.getExternalId())).body("_links[0].method", org.hamcrest.core.Is.is("GET")).body("_links[0].rel", org.hamcrest.core.Is.is("self"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn404_whenGetUser_withNonExistentExternalId() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, "non-existent-user")).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    void shouldReturn404_whenGetUser_withInvalidMaxLengthExternalId() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.USER_RESOURCE_URL, org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(256))).then().statusCode(404);
    }
}
