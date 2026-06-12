package uk.gov.pay.adminusers.resources;
public class ServiceResourceUpdateCustomBrandingIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenUpdatingCustomBranding() throws java.lang.Exception {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName("existing-name"));
        databaseHelper.addService(service, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString());
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("path", "custom_branding", "op", "replace", "value", java.util.Map.of("image_url", "image url", "css_url", "css url"));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId)).then().statusCode(200).body("custom_branding.image_url", org.hamcrest.core.Is.is("image url")).body("custom_branding.css_url", org.hamcrest.core.Is.is("css url"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReplaceWithEmpty_whenUpdatingCustomBranding_withEmptyObject() throws java.lang.Exception {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.util.Map<java.lang.String, java.lang.Object> existingBranding = java.util.Map.of("css_url", "existing css", "image_url", "existing image");
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName("existing-name"));
        service.setCustomBranding(existingBranding);
        java.util.Map<java.lang.String, java.lang.Object> payloadWithEmptyBranding = java.util.Map.of("path", "custom_branding", "op", "replace", "value", java.util.Collections.emptyMap());
        databaseHelper.addService(service, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString());
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payloadWithEmptyBranding)).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId)).then().statusCode(200).body("custom_branding", org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.nullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn400_whenUpdatingServiceCustomisations_ifPayloadNotJson() throws java.lang.Exception {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName("existing-name"));
        java.util.Map<java.lang.String, java.lang.Object> customBranding = java.util.Map.of("css_url", "existing css", "image_url", "existing image");
        service.setCustomBranding(customBranding);
        databaseHelper.addService(service, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString());
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("path", "custom_branding", "op", "replace", "value", "blah");
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId)).then().statusCode(400);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn404_whenUpdatingServiceCustomisations_ifNotFound() throws java.lang.Exception {
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("path", "custom_branding", "op", "replace", "value", java.util.Map.of("image_url", "image url", "css_url", "css url"));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, "non-existent-id")).then().statusCode(404);
    }
}
