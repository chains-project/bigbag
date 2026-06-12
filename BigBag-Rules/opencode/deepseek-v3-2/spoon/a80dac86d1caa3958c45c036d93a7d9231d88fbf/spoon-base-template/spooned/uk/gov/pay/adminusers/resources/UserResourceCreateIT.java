package uk.gov.pay.adminusers.resources;
public class UserResourceCreateIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    public void shouldCreateAUser_Successfully() throws java.lang.Exception {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.util.Map<java.lang.Object, java.lang.Object> userPayload = java.util.Map.of("username", username, "email", ("user-" + username) + "@example.com", "telephone_number", "+441134960000", "otp_key", "34f34", "role_name", "admin");
        io.restassured.response.ValidatableResponse response = givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(userPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_RESOURCE_URL).then();
        java.lang.String externalId = response.extract().path("external_id");
        response.statusCode(201).body("id", org.hamcrest.Matchers.nullValue()).body("external_id", org.hamcrest.core.Is.is(externalId)).body("username", org.hamcrest.core.Is.is(username)).body("password", org.hamcrest.Matchers.nullValue()).body("email", org.hamcrest.core.Is.is(("user-" + username) + "@example.com")).body("service_roles", org.hamcrest.Matchers.hasSize(0)).body("telephone_number", org.hamcrest.core.Is.is("+441134960000")).body("otp_key", org.hamcrest.core.Is.is("34f34")).body("login_counter", org.hamcrest.core.Is.is(0)).body("disabled", org.hamcrest.core.Is.is(false));
        response.body("_links", org.hamcrest.Matchers.hasSize(1)).body("_links[0].href", org.hamcrest.core.Is.is("http://localhost:8080/v1/api/users/" + externalId)).body("_links[0].method", org.hamcrest.core.Is.is("GET")).body("_links[0].rel", org.hamcrest.core.Is.is("self"));
        // TODO - WIP This will be removed when PP-1612 is done.
        // This is an extra check to verify that new created user gateways are registered withing the new Services Model as well as in users table
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> userByExternalId = databaseHelper.findUserByExternalId(externalId);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> servicesAssociatedToUser = databaseHelper.findUserServicesByUserId(((java.lang.Integer) (userByExternalId.get(0).get("id"))));
        org.hamcrest.MatcherAssert.assertThat(servicesAssociatedToUser.size(), org.hamcrest.core.Is.is(0));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateAUser_withinAService_IfServiceExternalIdsExists() throws java.lang.Exception {
        java.lang.String gatewayAccount1 = java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt());
        java.lang.String gatewayAccount2 = java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt());
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withGatewayAccountIds(gatewayAccount1, gatewayAccount2).insertService();
        java.lang.String serviceExternalId = service.getExternalId();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.util.Map<java.lang.Object, java.lang.Object> userPayload = java.util.Map.of("username", username, "email", ("user-" + username) + "@example.com", "service_external_ids", new java.lang.String[]{ java.lang.String.valueOf(serviceExternalId) }, "telephone_number", "+441134960000", "otp_key", "34f34", "role_name", "admin");
        io.restassured.response.ValidatableResponse response = givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(userPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_RESOURCE_URL).then();
        java.lang.String externalId = response.extract().path("external_id");
        response.statusCode(201).body("id", org.hamcrest.Matchers.nullValue()).body("external_id", org.hamcrest.core.Is.is(externalId)).body("username", org.hamcrest.core.Is.is(username)).body("password", org.hamcrest.Matchers.nullValue()).body("email", org.hamcrest.core.Is.is(("user-" + username) + "@example.com")).body("service_roles", org.hamcrest.Matchers.hasSize(1)).body("service_roles[0].service.external_id", org.hamcrest.core.Is.is(serviceExternalId)).body("service_roles[0].service.name", org.hamcrest.core.Is.is(service.getName())).body("telephone_number", org.hamcrest.core.Is.is("+441134960000")).body("otp_key", org.hamcrest.core.Is.is("34f34")).body("login_counter", org.hamcrest.core.Is.is(0)).body("disabled", org.hamcrest.core.Is.is(false)).body("service_roles[0].role.name", org.hamcrest.core.Is.is("admin")).body("service_roles[0].role.description", org.hamcrest.core.Is.is("Administrator")).body("service_roles[0].role.permissions.size()", org.hamcrest.core.Is.is(org.hamcrest.Matchers.greaterThan(1)));
        response.body("_links", org.hamcrest.Matchers.hasSize(1)).body("_links[0].href", org.hamcrest.core.Is.is("http://localhost:8080/v1/api/users/" + externalId)).body("_links[0].method", org.hamcrest.core.Is.is("GET")).body("_links[0].rel", org.hamcrest.core.Is.is("self"));
        // TODO - WIP This will be removed when PP-1612 is done.
        // This is an extra check to verify that new created user gateways are registered withing the new Services Model as well as in users table
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> userByExternalId = databaseHelper.findUserByExternalId(externalId);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> servicesAssociatedToUser = databaseHelper.findUserServicesByUserId(((java.lang.Integer) (userByExternalId.get(0).get("id"))));
        org.hamcrest.MatcherAssert.assertThat(servicesAssociatedToUser.size(), org.hamcrest.core.Is.is(1));
    }

    @org.junit.jupiter.api.Test
    public void shouldError400_IfRoleDoesNotExist() throws java.lang.Exception {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.util.Map<java.lang.Object, java.lang.Object> userPayload = java.util.Map.of("username", username, "email", ("user-" + username) + "@example.com", "gateway_account_ids", new java.lang.String[]{ "1", "2" }, "telephone_number", "01134960000", "otp_key", "34f34", "role_name", "invalid-role");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(userPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_RESOURCE_URL).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("role [invalid-role] not recognised"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError400_whenFieldsMissingForUserCreation() throws java.lang.Exception {
        java.util.Map<java.lang.Object, java.lang.Object> invalidPayload = java.util.Collections.emptyMap();
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(invalidPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_RESOURCE_URL).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(4)).body("errors", org.hamcrest.Matchers.hasItems("Field [username] is required", "Field [email] is required", "Field [telephone_number] is required", "Field [role_name] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldError409_IfUsernameAlreadyExists() throws java.lang.Exception {
        java.lang.String gatewayAccount = java.lang.String.valueOf(org.apache.commons.lang3.RandomUtils.nextInt());
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withGatewayAccountIds(gatewayAccount).insertService();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser();
        java.util.Map<java.lang.Object, java.lang.Object> userPayload = java.util.Map.of("username", username, "email", email, "gateway_account_ids", new java.lang.String[]{ gatewayAccount }, "telephone_number", "01134960000", "role_name", "admin");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(userPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_RESOURCE_URL).then().statusCode(409).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is(java.lang.String.format("username [%s] already exists", username)));
    }
}
