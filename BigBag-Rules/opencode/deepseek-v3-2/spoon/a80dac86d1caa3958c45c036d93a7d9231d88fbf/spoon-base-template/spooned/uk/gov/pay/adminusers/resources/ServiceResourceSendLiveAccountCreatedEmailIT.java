package uk.gov.pay.adminusers.resources;
public class ServiceResourceSendLiveAccountCreatedEmailIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    @org.junit.jupiter.api.Test
    public void shouldSendEmail() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture.govUkPayAgreementDbFixture(databaseHelper).withServiceId(service.getId()).insert();
        givenSetup().when().post(java.lang.String.format("/v1/api/services/%s/send-live-email", service.getExternalId())).then().statusCode(200);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn_404_whenServiceNotFound() {
        givenSetup().when().post(java.lang.String.format("/v1/api/services/%s/send-live-email", "123")).then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    public void shouldReturn_409_whenAgreementNotSigned() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).insertService();
        givenSetup().when().post(java.lang.String.format("/v1/api/services/%s/send-live-email", service.getExternalId())).then().statusCode(409).body("errors", org.hamcrest.Matchers.hasSize(1)).body("errors[0]", org.hamcrest.core.Is.is("Nobody from this service is on record as having agreed to the legal terms"));
    }
}
