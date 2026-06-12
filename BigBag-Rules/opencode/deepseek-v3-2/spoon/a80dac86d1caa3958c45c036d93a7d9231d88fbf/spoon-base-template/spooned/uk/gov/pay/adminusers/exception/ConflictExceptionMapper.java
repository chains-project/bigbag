package uk.gov.pay.adminusers.exception;
public class ConflictExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<uk.gov.pay.adminusers.exception.ConflictException> {
    @java.lang.Override
    public javax.ws.rs.core.Response toResponse(uk.gov.pay.adminusers.exception.ConflictException exception) {
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CONFLICT).entity(java.util.Map.of("errors", java.util.List.of(exception.getMessage()))).type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).build();
    }
}
