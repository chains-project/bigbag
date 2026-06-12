package uk.gov.pay.adminusers.filters;
public class LoggingMDCResponseFilter implements javax.ws.rs.container.ContainerResponseFilter {
    @java.lang.Override
    public void filter(javax.ws.rs.container.ContainerRequestContext requestContext, javax.ws.rs.container.ContainerResponseContext responseContext) throws java.io.IOException {
        java.util.List.of(uk.gov.service.payments.logging.LoggingKeys.SERVICE_EXTERNAL_ID, uk.gov.service.payments.logging.LoggingKeys.USER_EXTERNAL_ID).forEach(org.slf4j.MDC::remove);
    }
}
