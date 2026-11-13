package uk.gov.pay.adminusers.resources;
public class InvalidMerchantDetailsExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException> {
    @java.lang.Override
    public javax.ws.rs.core.Response toResponse(uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException exception) {
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR).build();
    }
}
