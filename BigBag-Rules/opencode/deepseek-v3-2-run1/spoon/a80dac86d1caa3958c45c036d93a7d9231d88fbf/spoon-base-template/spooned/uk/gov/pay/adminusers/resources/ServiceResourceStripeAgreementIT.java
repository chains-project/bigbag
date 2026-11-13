package uk.gov.pay.adminusers.resources;
class ServiceResourceStripeAgreementIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private uk.gov.pay.adminusers.model.Service service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
    }

    @org.junit.jupiter.api.Test
    void shouldCreateStripeAgreement() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of(uk.gov.pay.adminusers.model.StripeAgreement.FIELD_IP_ADDRESS, "0.0.0.0"));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/stripe-agreement", service.getExternalId())).then().statusCode(201);
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_NOT_FOUND_whenServiceNotFound() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of(uk.gov.pay.adminusers.model.StripeAgreement.FIELD_IP_ADDRESS, "0.0.0.0"));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/stripe-agreement", "123")).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_409_whenStripeAgreementAlreadyExists() {
        uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture.stripeAgreementDbFixture(databaseHelper).withServiceId(service.getId()).insert();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of(uk.gov.pay.adminusers.model.StripeAgreement.FIELD_IP_ADDRESS, "0.0.0.0"));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/stripe-agreement", service.getExternalId())).then().statusCode(409).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Stripe agreement information is already stored for this service"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_422_whenProvidedInvalidIPAddress() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of(uk.gov.pay.adminusers.model.StripeAgreement.FIELD_IP_ADDRESS, "257.0.0.0"));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/stripe-agreement", service.getExternalId())).then().statusCode(422).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("ipAddress must be valid IP address"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_422_whenIpAddressIsNotAString() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of(uk.gov.pay.adminusers.model.StripeAgreement.FIELD_IP_ADDRESS, 100));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/stripe-agreement", service.getExternalId())).then().statusCode(422).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("ipAddress must be valid IP address"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_422_whenIpAddressNotProvided() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Collections.emptyMap());
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/stripe-agreement", service.getExternalId())).then().statusCode(422).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("ipAddress must not be null"));
    }

    @org.junit.jupiter.api.Test
    void shouldGetStripeAgreementDetails() {
        java.time.ZonedDateTime agreementTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        java.lang.String ipAddress = "192.0.2.0";
        uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture.stripeAgreementDbFixture(databaseHelper).withServiceId(service.getId()).withIpAddress(ipAddress).withAgreementTime(agreementTime).insert();
        givenSetup().when().get(java.lang.String.format("/v1/api/services/%s/stripe-agreement", service.getExternalId())).then().statusCode(200).body("ip_address", org.hamcrest.core.Is.is(ipAddress)).body("agreement_time", org.hamcrest.core.Is.is(uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION.format(agreementTime)));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_NOT_FOUND_whenStripeAgreementDoesNotExist() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/stripe-agreement", service.getExternalId())).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_NOT_FOUND_whenServiceDoesNotExist() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/stripe-agreement", "NON_EXISTENT_SERVICE")).then().statusCode(404);
    }
}
