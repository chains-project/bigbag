package uk.gov.pay.adminusers.client.ledger.service;
public class LedgerService {
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.client.ledger.service.LedgerService.class);

    private final javax.ws.rs.client.Client client;

    private final java.lang.String ledgerUrl;

    @javax.inject.Inject
    public LedgerService(javax.ws.rs.client.Client client, uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration) {
        this.client = client;
        this.ledgerUrl = configuration.getLedgerBaseUrl();
    }

    public java.util.Optional<uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction> getTransaction(java.lang.String id) {
        var uri = javax.ws.rs.core.UriBuilder.fromPath(ledgerUrl).path(java.lang.String.format("/v1/transaction/%s", id)).queryParam("override_account_id_restriction", "true");
        logger.info("Querying ledger for transaction: {}", id);
        return getTransactionFromLedger(uri);
    }

    private java.util.Optional<uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction> getTransactionFromLedger(javax.ws.rs.core.UriBuilder uri) {
        javax.ws.rs.core.Response response = getResponse(uri);
        if (response.getStatus() == org.apache.http.HttpStatus.SC_OK) {
            return java.util.Optional.of(response.readEntity(uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction.class));
        }
        return java.util.Optional.empty();
    }

    private javax.ws.rs.core.Response getResponse(javax.ws.rs.core.UriBuilder uri) {
        return client.target(uri).request().accept(javax.ws.rs.core.MediaType.APPLICATION_JSON).get();
    }
}
