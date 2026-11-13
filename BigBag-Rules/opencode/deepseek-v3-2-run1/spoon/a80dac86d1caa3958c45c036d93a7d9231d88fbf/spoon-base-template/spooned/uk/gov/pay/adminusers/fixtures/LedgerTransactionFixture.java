package uk.gov.pay.adminusers.fixtures;
public class LedgerTransactionFixture {
    private java.lang.String transactionId;

    private java.lang.String reference;

    private LedgerTransactionFixture() {
    }

    public static uk.gov.pay.adminusers.fixtures.LedgerTransactionFixture aLedgerTransactionFixture() {
        return new uk.gov.pay.adminusers.fixtures.LedgerTransactionFixture();
    }

    public uk.gov.pay.adminusers.fixtures.LedgerTransactionFixture withTransactionId(java.lang.String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public uk.gov.pay.adminusers.fixtures.LedgerTransactionFixture withReference(java.lang.String reference) {
        this.reference = reference;
        return this;
    }

    public uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction build() {
        return new uk.gov.pay.adminusers.client.ledger.model.LedgerTransaction(transactionId, reference);
    }
}
