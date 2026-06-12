package uk.gov.pay.adminusers.client.ledger.model;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LedgerTransaction {
    private java.lang.String transactionId;

    private java.lang.String reference;

    public LedgerTransaction() {
        // empty constructor
    }

    public LedgerTransaction(java.lang.String transactionId, java.lang.String reference) {
        this.transactionId = transactionId;
        this.reference = reference;
    }

    public java.lang.String getReference() {
        return reference;
    }

    public void setReference(java.lang.String reference) {
        this.reference = reference;
    }

    public java.lang.String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(java.lang.String transactionId) {
        this.transactionId = transactionId;
    }
}
