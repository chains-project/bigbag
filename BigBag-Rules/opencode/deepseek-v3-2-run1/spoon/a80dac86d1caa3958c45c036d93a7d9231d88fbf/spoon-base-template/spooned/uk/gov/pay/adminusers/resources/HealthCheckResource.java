package uk.gov.pay.adminusers.resources;
@javax.ws.rs.Path("/")
public class HealthCheckResource {
    private final io.dropwizard.setup.Environment environment;

    @com.google.inject.Inject
    public HealthCheckResource(io.dropwizard.setup.Environment environment) {
        this.environment = environment;
    }

    @javax.ws.rs.GET
    @javax.ws.rs.Path("healthcheck")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Other", summary = "Healthcheck endpoint for adminusers. Check database, and deadlocks", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = (((((((("{" + "    \"database\": {") + "        \"healthy\": true,") + "        \"message\": \"Healthy\"") + "    },") + "    \"deadlocks\": {") + "        \"healthy\": true,") + "        \"message\": \"Healthy\"") + "    }") + "}")), description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Service unavailable. If any healthchecks fail") })
    public javax.ws.rs.core.Response healthCheck() {
        java.util.SortedMap<java.lang.String, com.codahale.metrics.health.HealthCheck.Result> results = environment.healthChecks().runHealthChecks();
        java.util.Map<java.lang.String, java.util.Map<java.lang.String, java.lang.Object>> response = results.entrySet().stream().collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, healthcheckResult -> java.util.Map.of("healthy", healthcheckResult.getValue().isHealthy(), "message", org.apache.commons.lang3.StringUtils.defaultString(healthcheckResult.getValue().getMessage(), "Healthy"))));
        javax.ws.rs.core.Response.Status status = (allHealthy(results.values())) ? javax.ws.rs.core.Response.Status.OK : javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;
        return javax.ws.rs.core.Response.status(status).entity(response).build();
    }

    private boolean allHealthy(java.util.Collection<com.codahale.metrics.health.HealthCheck.Result> results) {
        return results.stream().allMatch(com.codahale.metrics.health.HealthCheck.Result::isHealthy);
    }
}
