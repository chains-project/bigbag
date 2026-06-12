package uk.gov.pay.adminusers.client.ledger.service;
@org.junit.runner.RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class LedgerServiceConsumerTest {
    @org.junit.Rule
    public uk.gov.service.payments.commons.testing.pact.consumers.PactProviderRule ledgerRule = new uk.gov.service.payments.commons.testing.pact.consumers.PactProviderRule("ledger", this);

    @org.mockito.Mock
    uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration;

    private uk.gov.pay.adminusers.client.ledger.service.LedgerService ledgerService;

    @org.junit.Before
    public void setUp() {
        org.mockito.Mockito.when(configuration.getLedgerBaseUrl()).thenReturn(ledgerRule.getUrl());
        javax.ws.rs.client.Client client = uk.gov.pay.adminusers.app.RestClientFactory.buildClient(new uk.gov.pay.adminusers.app.config.RestClientConfig());
        ledgerService = new uk.gov.pay.adminusers.client.ledger.service.LedgerService(client, configuration);
    }

    @org.junit.Test
    @au.com.dius.pact.consumer.PactVerification("ledger")
    @uk.gov.service.payments.commons.testing.pact.consumers.Pacts(pacts = { "adminusers-ledger-get-payment-transaction" })
    public void getTransaction_shouldSerialiseLedgerPaymentTransactionCorrectly() {
        java.lang.String externalId = "e8eq11mi2ndmauvb51qsg8hccn";
        java.util.Optional<uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction> mayBeTransaction = ledgerService.getTransaction(externalId);
        org.hamcrest.MatcherAssert.assertThat(mayBeTransaction.isPresent(), org.hamcrest.CoreMatchers.is(true));
        uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction transaction = mayBeTransaction.get();
        org.hamcrest.MatcherAssert.assertThat(transaction.getTransactionId(), org.hamcrest.CoreMatchers.is(externalId));
        org.hamcrest.MatcherAssert.assertThat(transaction.getReference(), org.hamcrest.CoreMatchers.is(org.hamcrest.CoreMatchers.notNullValue()));
    }
}
