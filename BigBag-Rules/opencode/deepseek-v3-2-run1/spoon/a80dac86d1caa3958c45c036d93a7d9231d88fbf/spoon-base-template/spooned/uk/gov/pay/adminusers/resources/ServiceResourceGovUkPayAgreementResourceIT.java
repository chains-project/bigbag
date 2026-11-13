package uk.gov.pay.adminusers.resources;
class ServiceResourceGovUkPayAgreementResourceIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private uk.gov.pay.adminusers.model.Service service;

    private uk.gov.pay.adminusers.model.User user;

    private java.lang.String email;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        email = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid() + "@example.org";
        service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withServiceRole(service, 1).withEmail(email).insertUser();
    }

    @org.junit.jupiter.api.Test
    void shouldCreateGovUkPayAgreement() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("user_external_id", user.getExternalId()));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(201).body("email", org.hamcrest.core.Is.is(email));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_404_whenServiceNotFound() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("user_external_id", user.getExternalId()));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", "abcde1234")).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_400_whenUserNotFound() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("user_external_id", "abcde1234"));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(400).body("errors[0]", org.hamcrest.core.Is.is("Field [user_external_id] must be a valid user ID"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_400_whenUserExternalIdIsNotAString() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("user_external_id", 100));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Field [user_external_id] must be a valid user ID"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_400_whenUserExternalIdIsEmpty() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("user_external_id", ""));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Field [user_external_id] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_400_whenUserExternalIdIsMissing() {
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("user_id", user.getExternalId()));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(400).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Field [user_external_id] is required"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_409_whenAgreementAlreadyExists() {
        uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture.govUkPayAgreementDbFixture(databaseHelper).withServiceId(service.getId()).insert();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("user_external_id", user.getExternalId()));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(409).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("GOV.UK Pay agreement information is already stored for this service"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn_400_whenUserDoesNotBelongToService() {
        user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).insertUser();
        com.fasterxml.jackson.databind.JsonNode payload = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(java.util.Map.of("user_external_id", user.getExternalId()));
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(payload).post(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(400).body("errors[0]", org.hamcrest.core.Is.is("User does not belong to the given service"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnAgreement_whenExists() {
        java.time.ZonedDateTime agreementTime = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture.govUkPayAgreementDbFixture(databaseHelper).withServiceId(service.getId()).withEmail(user.getEmail()).withAgreementTime(agreementTime).insert();
        final java.lang.String expectedAgreementDate = uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION.format(agreementTime);
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(200).body("email", org.hamcrest.core.Is.is(user.getEmail())).body("agreement_time", org.hamcrest.core.Is.is(expectedAgreementDate));
    }

    @org.junit.jupiter.api.Test
    void shouldReturn404_whenAgreementNotExists() {
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).get(java.lang.String.format("/v1/api/services/%s/govuk-pay-agreement", service.getExternalId())).then().statusCode(404);
    }
}
