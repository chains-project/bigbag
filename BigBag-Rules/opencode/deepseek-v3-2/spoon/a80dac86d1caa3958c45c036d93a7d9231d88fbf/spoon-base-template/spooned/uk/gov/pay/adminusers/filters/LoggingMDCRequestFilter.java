package uk.gov.pay.adminusers.filters;
public class LoggingMDCRequestFilter implements javax.ws.rs.container.ContainerRequestFilter {
    @java.lang.Override
    public void filter(javax.ws.rs.container.ContainerRequestContext requestContext) throws java.io.IOException {
        getPathParameterFromRequest("serviceExternalId", requestContext).ifPresent(serviceExternalId -> org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.SERVICE_EXTERNAL_ID, serviceExternalId));
        getPathParameterFromRequest("userExternalId", requestContext).ifPresent(userExternalId -> org.slf4j.MDC.put(uk.gov.service.payments.logging.LoggingKeys.USER_EXTERNAL_ID, userExternalId));
    }

    private java.util.Optional<java.lang.String> getPathParameterFromRequest(java.lang.String parameterName, javax.ws.rs.container.ContainerRequestContext requestContext) {
        return java.util.Optional.ofNullable(requestContext.getUriInfo().getPathParameters().getFirst(parameterName));
    }
}
