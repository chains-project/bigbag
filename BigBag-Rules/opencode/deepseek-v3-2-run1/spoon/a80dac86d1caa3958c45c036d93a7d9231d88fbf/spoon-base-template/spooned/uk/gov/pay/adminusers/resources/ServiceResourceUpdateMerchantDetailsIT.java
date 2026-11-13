package uk.gov.pay.adminusers.resources;
public class ServiceResourceUpdateMerchantDetailsIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenUpdatingMerchantDetails() throws java.lang.Exception {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName("existing-name"));
        databaseHelper.addService(service, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString());
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("name", "somename", "telephone_number", "03069990000", "address_line1", "line1", "address_line2", "line2", "address_city", "city", "address_country", "country", "address_postcode", "postcode", "email", "dd-merchant@example.com", "url", "https://merchant.example.com");
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).put(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId) + "/merchant-details").then().statusCode(200).body("merchant_details.name", org.hamcrest.core.Is.is("somename")).body("merchant_details.telephone_number", org.hamcrest.core.Is.is("03069990000")).body("merchant_details.address_line1", org.hamcrest.core.Is.is("line1")).body("merchant_details.address_line2", org.hamcrest.core.Is.is("line2")).body("merchant_details.address_city", org.hamcrest.core.Is.is("city")).body("merchant_details.address_country", org.hamcrest.core.Is.is("country")).body("merchant_details.address_postcode", org.hamcrest.core.Is.is("postcode")).body("merchant_details.email", org.hamcrest.core.Is.is("dd-merchant@example.com")).body("merchant_details.url", org.hamcrest.core.Is.is("https://merchant.example.com"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenUpdatingMerchantDetails_withoutOptionalFields() throws java.lang.Exception {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName("existing-name"));
        databaseHelper.addService(service, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString());
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("name", "somename", "address_line1", "line1", "address_city", "city", "address_country", "country", "address_postcode", "postcode");
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).put(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId) + "/merchant-details").then().statusCode(200).body("merchant_details.name", org.hamcrest.core.Is.is("somename")).body("merchant_details.address_line1", org.hamcrest.core.Is.is("line1")).body("merchant_details.address_city", org.hamcrest.core.Is.is("city")).body("merchant_details.address_country", org.hamcrest.core.Is.is("country")).body("merchant_details.address_postcode", org.hamcrest.core.Is.is("postcode")).body("merchant_details", org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("telephone_number"))).body("merchant_details", org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("address_line2"))).body("merchant_details", org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("email"))).body("merchant_details", org.hamcrest.CoreMatchers.not(org.hamcrest.Matchers.hasKey("url")));
    }

    @org.junit.jupiter.api.Test
    public void shouldFail_whenUpdatingMerchantDetails_withMissingMandatoryFieldName() throws java.lang.Exception {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), serviceExternalId, new uk.gov.pay.adminusers.model.ServiceName("existing-name"));
        databaseHelper.addService(service, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString());
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("telephone_number", "03069990000", "address_line1", "line1", "address_line2", "line2", "address_city", "city", "address_country", "country", "address_postcode", "postcode");
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).accept(io.restassured.http.ContentType.JSON).body(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.writeValueAsString(payload)).put(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId) + "/merchant-details").then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors", org.hamcrest.Matchers.hasItems("Field [name] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldSucceed_whenPatchUpdatingMultipleMerchantDetails() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService().getExternalId();
        java.lang.String addressLine1 = "1 Spider Lane";
        java.lang.String addressCountry = "Somewhere";
        com.fasterxml.jackson.databind.node.ArrayNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.createArrayNode();
        payload.add(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "replace", "path", "merchant_details/address_line1", "value", addressLine1)));
        payload.add(uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "replace", "path", "merchant_details/address_country", "value", addressCountry)));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId)).then().statusCode(200).body("merchant_details.address_line1", org.hamcrest.core.Is.is(addressLine1)).body("merchant_details.address_country", org.hamcrest.core.Is.is(addressCountry));
    }

    @org.junit.jupiter.api.Test
    public void shouldSucceed_whenPatchUpdatingAddressLine1AndMerchantDetailsIsNull() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withMerchantDetails(null).insertService().getExternalId();
        java.lang.String addressLine1 = "1 Spider Lane";
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("op", "replace", "path", "merchant_details/address_line1", "value", addressLine1));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId)).then().statusCode(200).body("merchant_details.address_line1", org.hamcrest.core.Is.is(addressLine1));
    }
}
