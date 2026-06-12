package uk.gov.pay.adminusers.resources;
public class InviteResourceCreateServiceIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    public static final java.lang.String SERVICE_INVITES_CREATE_URL = "/v1/api/invites/service";

    @org.junit.jupiter.api.Test
    public void shouldSuccess_WhenAllRequiredFieldsAreProvidedAndValid() throws java.lang.Exception {
        java.lang.String email = "example@example.gov.uk";
        java.lang.String telephoneNumber = "01134960000";
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("telephone_number", telephoneNumber, "email", email, "password", "plain_text_password");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.InviteResourceCreateServiceIT.SERVICE_INVITES_CREATE_URL).then().statusCode(javax.ws.rs.core.Response.Status.CREATED.getStatusCode()).body("email", org.hamcrest.core.Is.is(email.toLowerCase(java.util.Locale.ENGLISH))).body("telephone_number", org.hamcrest.core.Is.is("+441134960000")).body("_links", org.hamcrest.Matchers.hasSize(2)).body("_links[0].href", org.hamcrest.text.MatchesPattern.matchesPattern("^https://selfservice.pymnt.localdomain/invites/[0-9a-z]{32}$")).body("_links[0].method", org.hamcrest.core.Is.is("GET")).body("_links[0].rel", org.hamcrest.core.Is.is("invite"));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_WhenMandatoryFieldsAreMissing() throws java.lang.Exception {
        java.lang.String telephoneNumber = "07700900000";
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("telephone_number", telephoneNumber, "password", "plain_text_password");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.InviteResourceCreateServiceIT.SERVICE_INVITES_CREATE_URL).then().statusCode(javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode()).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors", org.hamcrest.Matchers.hasItems("Field [email] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_WhenEmailIsNotPublicSectorDomain() throws java.lang.Exception {
        java.lang.String email = "example@example.com";
        java.lang.String telephoneNumber = "07700900000";
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("telephone_number", telephoneNumber, "email", email, "password", "plain_text_password");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.InviteResourceCreateServiceIT.SERVICE_INVITES_CREATE_URL).then().statusCode(javax.ws.rs.core.Response.Status.FORBIDDEN.getStatusCode()).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors", org.hamcrest.Matchers.hasItems(("Email [" + email) + "] is not a valid public sector email"));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_WhenEmailIsAlreadyRegistered() throws java.lang.Exception {
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.gov.uk";
        uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withUsername(username).withEmail(email).insertUser();
        java.lang.String telephoneNumber = "01134960000";
        java.util.Map<java.lang.String, java.lang.String> payload = java.util.Map.of("telephone_number", telephoneNumber, "email", email, "password", "plain_text_password");
        givenSetup().when().body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).contentType(io.restassured.http.ContentType.JSON).post(uk.gov.pay.adminusers.resources.InviteResourceCreateServiceIT.SERVICE_INVITES_CREATE_URL).then().statusCode(javax.ws.rs.core.Response.Status.CONFLICT.getStatusCode()).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors", org.hamcrest.Matchers.hasItems(("email [" + email) + "] already exists"));
    }
}
