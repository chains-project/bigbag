package uk.gov.pay.adminusers.client.ledger.exception;
public class LedgerException extends java.lang.RuntimeException {
    private java.lang.Integer status;

    public LedgerException(javax.ws.rs.core.Response response) {
        super(response.toString());
        status = response.getStatus();
    }

    public LedgerException(java.lang.Exception exception) {
        super(exception);
    }

    @java.lang.Override
    public java.lang.String toString() {
        return (((("LedgerException{" + "status=") + status) + ", message=") + getMessage()) + '}';
    }
}
