package uk.gov.pay.adminusers.resources;
public class EmailResourceIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private static final java.lang.String GATEWAY_ACCOUNT_ID = "DIRECT_DEBIT:mdshfsehdtfsdtjg";

    private java.util.Map<java.lang.String, java.lang.Object> validEmailRequest = java.util.Map.of("address", "cake@directdebitteam.test", "gateway_account_external_id", uk.gov.pay.adminusers.resources.EmailResourceIT.GATEWAY_ACCOUNT_ID, "template", "MANDATE_CANCELLED", "personalisation", java.util.Map.of("mandate reference", "mandatereference", "org name", "cake service"));

    @org.junit.jupiter.api.Test
    public void shouldReceiveAPayloadAndSendEmail() {
        uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(databaseHelper).withGatewayAccountIds(uk.gov.pay.adminusers.resources.EmailResourceIT.GATEWAY_ACCOUNT_ID).withMerchantDetails(new uk.gov.pay.adminusers.model.MerchantDetails("name", "number", "line1", null, "city", "postcode", "country", "dd-merchant@example.com", "https://merchant.example.org")).insertService();
        java.lang.String body = uk.gov.pay.adminusers.resources.IntegrationTest.mapper.valueToTree(validEmailRequest).toString();
        givenSetup().when().accept(io.restassured.http.ContentType.JSON).body(body).post("/v1/emails/send").then().statusCode(200);
    }
}
