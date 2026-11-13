package uk.gov.pay.adminusers.resources;
class ServiceResourceCreateIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    void shouldCreateService() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("service_name", java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString(), "Service name"), "gateway_account_ids", java.util.List.of("1")));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).post("v1/api/services").then().statusCode(201).body("created_date", org.hamcrest.Matchers.is(org.hamcrest.Matchers.not(org.hamcrest.Matchers.nullValue()))).body("gateway_account_ids", org.hamcrest.Matchers.is(java.util.List.of("1"))).body("service_name", org.hamcrest.Matchers.hasEntry("en", "Service name"));
    }
}
