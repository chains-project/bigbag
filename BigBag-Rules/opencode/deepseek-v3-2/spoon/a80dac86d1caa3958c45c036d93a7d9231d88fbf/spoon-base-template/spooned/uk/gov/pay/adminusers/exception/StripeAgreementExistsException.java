package uk.gov.pay.adminusers.exception;
public class StripeAgreementExistsException extends uk.gov.pay.adminusers.exception.ConflictException {
    public StripeAgreementExistsException() {
        super("Stripe agreement information is already stored for this service");
    }
}
