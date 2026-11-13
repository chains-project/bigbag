package uk.gov.pay.adminusers.resources;
@javax.ws.rs.Path("/v1/api/toolbox")
public class ToolboxEndpointResource {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.ToolboxEndpointResource.class);

    private final uk.gov.pay.adminusers.service.ServiceUserRemover serviceUserRemover;

    @javax.inject.Inject
    public ToolboxEndpointResource(uk.gov.pay.adminusers.service.ServiceUserRemover serviceUserRemover) {
        this.serviceUserRemover = serviceUserRemover;
    }

    @javax.ws.rs.Path("/services/{serviceExternalId}/users/{userExternalId}")
    @javax.ws.rs.DELETE
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Toolbox", summary = "Remove user from service (Toolbox use only)", operationId = "removeUserFromServiceUsingToolbox", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Delete user from service"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User or Service not found") })
    public javax.ws.rs.core.Response removeUserFromService(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId, @io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String userExternalId) {
        uk.gov.pay.adminusers.resources.ToolboxEndpointResource.LOGGER.info(java.lang.String.format("Toolbox DELETE request - removing user %s from service %s,", serviceExternalId, userExternalId), net.logstash.logback.argument.StructuredArguments.kv(uk.gov.service.payments.logging.LoggingKeys.SERVICE_EXTERNAL_ID, serviceExternalId), net.logstash.logback.argument.StructuredArguments.kv(uk.gov.service.payments.logging.LoggingKeys.USER_EXTERNAL_ID, userExternalId));
        serviceUserRemover.removeWithoutAdminCheck(userExternalId, serviceExternalId);
        uk.gov.pay.adminusers.resources.ToolboxEndpointResource.LOGGER.info(java.lang.String.format("Succeeded toolbox users DELETE request - user %s removed from service %s", serviceExternalId, userExternalId), net.logstash.logback.argument.StructuredArguments.kv(uk.gov.service.payments.logging.LoggingKeys.SERVICE_EXTERNAL_ID, serviceExternalId), net.logstash.logback.argument.StructuredArguments.kv(uk.gov.service.payments.logging.LoggingKeys.USER_EXTERNAL_ID, userExternalId));
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NO_CONTENT).build();
    }
}
