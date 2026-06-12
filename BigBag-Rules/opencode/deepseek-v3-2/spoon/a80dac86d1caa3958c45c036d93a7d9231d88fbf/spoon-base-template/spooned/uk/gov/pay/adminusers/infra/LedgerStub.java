package uk.gov.pay.adminusers.infra;
public class LedgerStub {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private final com.github.tomakehurst.wiremock.WireMockServer wireMockServer;

    public LedgerStub(com.github.tomakehurst.wiremock.WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }

    public void returnLedgerTransaction(java.lang.String externalId, uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction ledgerTransaction) throws com.fasterxml.jackson.core.JsonProcessingException {
        com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response = com.github.tomakehurst.wiremock.client.WireMock.aResponse().withHeader(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE, javax.ws.rs.core.MediaType.APPLICATION_JSON).withStatus(200).withBody(uk.gov.pay.adminusers.infra.LedgerStub.objectMapper.writeValueAsString(ledgerTransaction));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo(java.lang.String.format("/v1/transaction/%s", externalId))).withQueryParam("override_account_id_restriction", com.github.tomakehurst.wiremock.client.WireMock.equalTo("true")).willReturn(response));
    }
}
