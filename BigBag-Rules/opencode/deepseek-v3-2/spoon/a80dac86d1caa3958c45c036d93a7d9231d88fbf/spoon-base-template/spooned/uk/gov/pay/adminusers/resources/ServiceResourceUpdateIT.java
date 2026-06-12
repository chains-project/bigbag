package uk.gov.pay.adminusers.resources;
public class ServiceResourceUpdateIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    public void shouldUpdateServiceFields() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService().getExternalId();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.List.of(patchRequest("replace", "current_go_live_stage", "CHOSEN_PSP_STRIPE"), patchRequest("replace", "redirect_to_service_immediately_on_terminal_state", true), patchRequest("replace", "experimental_features_enabled", true), patchRequest("replace", "agent_initiated_moto_enabled", true), patchRequest("replace", "collect_billing_address", true), patchRequest("replace", "sector", "local government"), patchRequest("replace", "internal", true), patchRequest("replace", "archived", true), patchRequest("replace", "went_live_date", "2020-01-01T01:01:00Z"), patchRequest("replace", "current_psp_test_account_stage", "REQUEST_SUBMITTED"), patchRequest("replace", "default_billing_address_country", "IE")));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId)).then().statusCode(200).body("current_go_live_stage", org.hamcrest.core.Is.is(java.lang.String.valueOf(uk.gov.pay.adminusers.model.GoLiveStage.CHOSEN_PSP_STRIPE))).body("redirect_to_service_immediately_on_terminal_state", org.hamcrest.core.Is.is(true)).body("experimental_features_enabled", org.hamcrest.core.Is.is(true)).body("agent_initiated_moto_enabled", org.hamcrest.core.Is.is(true)).body("collect_billing_address", org.hamcrest.core.Is.is(true)).body("sector", org.hamcrest.core.Is.is("local government")).body("internal", org.hamcrest.core.Is.is(true)).body("archived", org.hamcrest.core.Is.is(true)).body("went_live_date", org.hamcrest.core.Is.is("2020-01-01T01:01:00.000Z")).body("current_psp_test_account_stage", org.hamcrest.core.Is.is("REQUEST_SUBMITTED")).body("default_billing_address_country", org.hamcrest.core.Is.is("IE"));
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateDefaultBillingAddressCountryToNull() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService().getExternalId();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.List.of(patchRequest("replace", "default_billing_address_country", null)));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId)).then().statusCode(200).body("default_billing_address_country", org.hamcrest.core.Is.is(org.hamcrest.Matchers.nullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateMerchantUrlField() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withMerchantDetails(new uk.gov.pay.adminusers.model.MerchantDetails("name", null, "line1", null, "city", "postcode", "country", null, "https://www.example.com")).insertService().getExternalId();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.List.of(patchRequest("replace", "merchant_details/url", "https://merchant.example.com")));
        givenSetup().when().contentType(io.restassured.http.ContentType.JSON).body(payload).patch(java.lang.String.format(uk.gov.pay.adminusers.resources.IntegrationTest.SERVICE_RESOURCE, serviceExternalId)).then().statusCode(200).body("merchant_details.url", org.hamcrest.core.Is.is("https://merchant.example.com"));
    }

    private java.util.Map<java.lang.String, java.lang.Object> patchRequest(java.lang.String op, java.lang.String path, java.lang.Object value) {
        java.util.Map<java.lang.String, java.lang.Object> request = new java.util.HashMap<>();
        request.put("path", path);
        request.put("op", op);
        request.put("value", value);
        return request;
    }
}
