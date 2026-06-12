package uk.gov.pay.adminusers.resources;
public class UserResourceFindIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private java.lang.String username;

    @org.junit.jupiter.api.BeforeEach
    public void createAUser() {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser();
        this.username = user.getUsername();
    }

    @org.junit.jupiter.api.Test
    public void shouldFindSuccessfully_existingUserByUserName() throws java.lang.Exception {
        java.util.Map<java.lang.String, java.lang.String> findPayload = java.util.Map.of("username", username);
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(findPayload)).post(uk.gov.pay.adminusers.resources.IntegrationTest.FIND_RESOURCE_URL).then().statusCode(200).body("username", org.hamcrest.core.Is.is(username));
    }

    @org.junit.jupiter.api.Test
    public void shouldError404_ifUserNotFound() throws java.lang.Exception {
        java.util.Map<java.lang.String, java.lang.String> findPayload = java.util.Map.of("username", "unknown-user@somewhere.com");
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(findPayload)).post(uk.gov.pay.adminusers.resources.IntegrationTest.FIND_RESOURCE_URL).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    public void shouldError400_ifFieldsMissing() throws java.lang.Exception {
        java.util.Map<java.lang.String, java.lang.String> findPayload = java.util.Map.of("", "");
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(findPayload)).post(uk.gov.pay.adminusers.resources.IntegrationTest.FIND_RESOURCE_URL).then().statusCode(400);
    }
}
