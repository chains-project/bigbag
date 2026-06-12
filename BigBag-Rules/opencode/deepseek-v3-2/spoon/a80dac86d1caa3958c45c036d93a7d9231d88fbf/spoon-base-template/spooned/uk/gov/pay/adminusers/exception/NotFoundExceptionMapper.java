package uk.gov.pay.adminusers.exception;
public class NotFoundExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<uk.gov.pay.adminusers.exception.NotFoundException> {
    @java.lang.Override
    public javax.ws.rs.core.Response toResponse(uk.gov.pay.adminusers.exception.NotFoundException exception) {
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build();
    }
}
