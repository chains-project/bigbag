package uk.gov.pay.adminusers.resources;
public class UserResourceAuthenticationIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    public void shouldAuthenticateUser_onAValidUsernamePasswordCombination() throws java.lang.Exception {
        java.lang.String[] gatewayAccountIds = new java.lang.String[]{ "1", "2" };
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withGatewayAccountIds(gatewayAccountIds).insertService();
        java.lang.String username = createAValidUser(service);
        java.util.Map<java.lang.Object, java.lang.Object> authPayload = java.util.Map.of("username", username, "password", "password-" + username);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(authPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_AUTHENTICATE_URL).then().statusCode(200).body("username", org.hamcrest.core.Is.is(username)).body("email", org.hamcrest.core.Is.is(("user-" + username) + "@example.com")).body("service_roles", org.hamcrest.Matchers.hasSize(1)).body("service_roles[0].service.external_id", org.hamcrest.core.Is.is(org.hamcrest.Matchers.notNullValue())).body("service_roles[0].service.name", org.hamcrest.core.Is.is(org.hamcrest.Matchers.notNullValue())).body("telephone_number", org.hamcrest.core.Is.is("+441134960000")).body("otp_key", org.hamcrest.core.Is.is("34f34")).body("login_counter", org.hamcrest.core.Is.is(0)).body("disabled", org.hamcrest.core.Is.is(false)).body("_links", org.hamcrest.Matchers.hasSize(1)).body("service_roles[0].role.name", org.hamcrest.core.Is.is("admin")).body("service_roles[0].role.permissions.size()", org.hamcrest.Matchers.greaterThan(1));
    }

    @org.junit.jupiter.api.Test
    public void shouldAuthenticateUser_onAValidUsernamePasswordCombination_whenUserDoesNotBelongToAService() throws java.lang.Exception {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid() + "@example.com";
        java.lang.String email = username;
        java.lang.String password = "password-" + username;
        java.lang.String encryptedPassword = new uk.gov.pay.adminusers.service.PasswordHasher().hash(password);
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).withPassword(encryptedPassword).insertUser();
        java.util.Map<java.lang.Object, java.lang.Object> authPayload = java.util.Map.of("username", username, "password", password);
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(authPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_AUTHENTICATE_URL).then().statusCode(200).body("username", org.hamcrest.core.Is.is(username)).body("service_roles", org.hamcrest.Matchers.hasSize(0)).body("_links", org.hamcrest.Matchers.hasSize(1));
    }

    @org.junit.jupiter.api.Test
    public void shouldAuthenticateFail_onAInvalidUsernamePasswordCombination() throws java.lang.Exception {
        java.lang.String[] gatewayAccountIds = new java.lang.String[]{ "3", "4" };
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withGatewayAccountIds(gatewayAccountIds).insertService();
        java.lang.String username = createAValidUser(service);
        java.util.Map<java.lang.Object, java.lang.Object> authPayload = java.util.Map.of("username", username, "password", "invalid-password");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(authPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_AUTHENTICATE_URL).then().statusCode(401).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("invalid username and/or password"));
    }

    private java.lang.String createAValidUser(uk.gov.pay.adminusers.model.Service service) throws com.fasterxml.jackson.core.JsonProcessingException {
        java.lang.String username = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(10) + java.util.UUID.randomUUID();
        java.util.Map<java.lang.Object, java.lang.Object> userPayload = java.util.Map.of("username", username, "password", "password-" + username, "email", ("user-" + username) + "@example.com", "service_external_ids", new java.lang.String[]{ service.getExternalId() }, "telephone_number", "+441134960000", "otp_key", "34f34", "role_name", "admin");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(userPayload)).contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.IntegrationTest.USERS_RESOURCE_URL).then().statusCode(201);
        return username;
    }
}
