package uk.gov.pay.adminusers.resources;
public class InvalidEmailRequestExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<uk.gov.pay.adminusers.resources.InvalidEmailRequestException> {
    @java.lang.Override
    public javax.ws.rs.core.Response toResponse(uk.gov.pay.adminusers.resources.InvalidEmailRequestException exception) {
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).build();
    }
}
