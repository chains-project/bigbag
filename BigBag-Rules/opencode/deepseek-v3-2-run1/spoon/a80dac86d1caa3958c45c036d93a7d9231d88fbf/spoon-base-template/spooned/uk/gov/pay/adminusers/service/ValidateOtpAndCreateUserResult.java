package uk.gov.pay.adminusers.service;
/**
 * This class is used to wrap the successful result or wrap thrown exception in result.
 * Similar to "Either" pattern: http://www.vavr.io/vavr-docs/#_either
 *
 * TODO: This class should be removed after refactoring of second factor authentication
 */
public class ValidateOtpAndCreateUserResult {
    private uk.gov.pay.adminusers.model.User user = null;

    private javax.ws.rs.WebApplicationException error;

    public ValidateOtpAndCreateUserResult(uk.gov.pay.adminusers.model.User user) {
        this.user = user;
    }

    public ValidateOtpAndCreateUserResult(javax.ws.rs.WebApplicationException error) {
        this.error = error;
    }

    public uk.gov.pay.adminusers.model.User getUser() {
        return user;
    }

    public javax.ws.rs.WebApplicationException getError() {
        return error;
    }

    public boolean isError() {
        return error != null;
    }
}
