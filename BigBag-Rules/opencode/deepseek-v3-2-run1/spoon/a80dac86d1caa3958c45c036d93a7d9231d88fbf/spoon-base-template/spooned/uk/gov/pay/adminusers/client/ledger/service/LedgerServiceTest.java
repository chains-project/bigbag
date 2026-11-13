package uk.gov.pay.adminusers.client.ledger.service;
@org.junit.runner.RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class LedgerServiceTest {
    @org.mockito.Mock
    uk.gov.pay.adminusers.app.config.AdminUsersConfig mockConfiguration;

    @org.mockito.Mock
    private javax.ws.rs.client.Client mockClient;

    @org.mockito.Mock
    private javax.ws.rs.client.WebTarget mockWebTarget;

    @org.mockito.Mock
    private javax.ws.rs.client.Invocation.Builder mockInvocationBuilder;

    @org.mockito.Mock
    private javax.ws.rs.core.Response mockResponse;

    private uk.gov.pay.adminusers.client.ledger.service.LedgerService serviceUnderTest;

    private static final java.lang.String LEDGER_URL = "http://ledgerUrl";

    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.Before
    public void setUp() {
        org.mockito.Mockito.when(mockConfiguration.getLedgerBaseUrl()).thenReturn(uk.gov.pay.adminusers.client.ledger.service.LedgerServiceTest.LEDGER_URL);
        org.mockito.Mockito.when(mockClient.target(org.mockito.ArgumentMatchers.any(javax.ws.rs.core.UriBuilder.class))).thenReturn(mockWebTarget);
        org.mockito.Mockito.when(mockWebTarget.request()).thenReturn(mockInvocationBuilder);
        org.mockito.Mockito.when(mockInvocationBuilder.accept(javax.ws.rs.core.MediaType.APPLICATION_JSON)).thenReturn(mockInvocationBuilder);
        org.mockito.Mockito.when(mockInvocationBuilder.get()).thenReturn(mockResponse);
        org.mockito.Mockito.when(mockResponse.getStatus()).thenReturn(org.apache.http.HttpStatus.SC_OK);
        serviceUnderTest = new uk.gov.pay.adminusers.client.ledger.service.LedgerService(mockClient, mockConfiguration);
    }

    @org.junit.Test
    public void getTransaction_shouldDeserialiseLedgerPaymentTransactionCorrectly() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.lang.String externalId = "e8eq11mi2ndmauvb51qsg8hccn";
        com.google.common.collect.ImmutableMap<java.lang.String, java.lang.String> transactionData = com.google.common.collect.ImmutableMap.of("transaction_id", externalId, "reference", "test event ref", "foo", "bar");
        java.lang.String ledgerPayload = new com.google.gson.Gson().toJson(transactionData);
        org.mockito.Mockito.when(mockResponse.readEntity(uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction.class)).thenReturn(uk.gov.pay.adminusers.client.ledger.service.LedgerServiceTest.objectMapper.readValue(ledgerPayload, uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction.class));
        java.util.Optional<uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction> mayBeTransaction = serviceUnderTest.getTransaction(externalId);
        org.hamcrest.MatcherAssert.assertThat(mayBeTransaction.isPresent(), org.hamcrest.CoreMatchers.is(true));
        uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction transaction = mayBeTransaction.get();
        org.hamcrest.MatcherAssert.assertThat(transaction.getTransactionId(), org.hamcrest.CoreMatchers.is(externalId));
        org.hamcrest.MatcherAssert.assertThat(transaction.getReference(), org.hamcrest.CoreMatchers.is("test event ref"));
    }
}
