package uk.gov.pay.adminusers.exception;
public class ValidationExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<uk.gov.pay.adminusers.exception.ValidationException> {
    @java.lang.Override
    public javax.ws.rs.core.Response toResponse(uk.gov.pay.adminusers.exception.ValidationException exception) {
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(exception.getErrors()).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).build();
    }
}
