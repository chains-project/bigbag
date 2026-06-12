package uk.gov.pay.adminusers.resources;
class ServiceResourceUpdateNameIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private java.lang.String serviceExternalId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        serviceExternalId = service.getExternalId();
    }

    @org.junit.jupiter.api.Test
    void shouldUpdateBothNameAndEnglishServiceName_whenUpdatingEnglishName() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "replace", "path", "service_name/en", "value", "New Service Name"));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format("/v1/api/services/%s", serviceExternalId)).then().statusCode(200).body("name", org.hamcrest.core.Is.is("New Service Name")).body("service_name.en", org.hamcrest.core.Is.is("New Service Name"));
    }
}
