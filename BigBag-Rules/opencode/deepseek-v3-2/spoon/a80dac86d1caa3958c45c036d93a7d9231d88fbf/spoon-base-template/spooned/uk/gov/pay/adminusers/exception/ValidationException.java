package uk.gov.pay.adminusers.exception;
public class ValidationException extends java.lang.Exception {
    private uk.gov.pay.adminusers.utils.Errors errors;

    public ValidationException(uk.gov.pay.adminusers.utils.Errors errors) {
        super();
        this.errors = errors;
    }

    public uk.gov.pay.adminusers.utils.Errors getErrors() {
        return errors;
    }
}
