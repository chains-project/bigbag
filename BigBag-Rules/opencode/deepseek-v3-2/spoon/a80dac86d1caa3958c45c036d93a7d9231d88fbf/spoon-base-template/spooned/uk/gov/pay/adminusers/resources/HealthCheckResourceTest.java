package uk.gov.pay.adminusers.resources;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class HealthCheckResourceTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.mockito.Mock
    private io.dropwizard.setup.Environment environment;

    @org.mockito.Mock
    private com.codahale.metrics.health.HealthCheckRegistry healthCheckRegistry;

    private uk.gov.pay.adminusers.resources.HealthCheckResource resource;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.when(environment.healthChecks()).thenReturn(healthCheckRegistry);
        resource = new uk.gov.pay.adminusers.resources.HealthCheckResource(environment);
    }

    @org.junit.jupiter.api.Test
    void checkHealthCheck_isUnHealthy() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.util.SortedMap<java.lang.String, com.codahale.metrics.health.HealthCheck.Result> map = new java.util.TreeMap<>();
        map.put("ping", com.codahale.metrics.health.HealthCheck.Result.unhealthy("application is unavailable"));
        map.put("deadlocks", com.codahale.metrics.health.HealthCheck.Result.unhealthy("no new threads available"));
        org.mockito.Mockito.when(healthCheckRegistry.runHealthChecks()).thenReturn(map);
        javax.ws.rs.core.Response response = resource.healthCheck();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(503));
        com.fasterxml.jackson.databind.ObjectWriter ow = uk.gov.pay.adminusers.resources.HealthCheckResourceTest.objectMapper.writer().withDefaultPrettyPrinter();
        java.lang.String body = ow.writeValueAsString(response.getEntity());
        com.jayway.jsonassert.JsonAssert.with(body).assertThat("$.*", org.hamcrest.Matchers.hasSize(2)).assertThat("$.ping.healthy", org.hamcrest.core.Is.is(false)).assertThat("$.deadlocks.healthy", org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    void checkHealthCheck_isHealthy() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.util.SortedMap<java.lang.String, com.codahale.metrics.health.HealthCheck.Result> map = new java.util.TreeMap<>();
        map.put("ping", com.codahale.metrics.health.HealthCheck.Result.healthy());
        map.put("deadlocks", com.codahale.metrics.health.HealthCheck.Result.healthy());
        org.mockito.Mockito.when(healthCheckRegistry.runHealthChecks()).thenReturn(map);
        javax.ws.rs.core.Response response = resource.healthCheck();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(200));
        com.fasterxml.jackson.databind.ObjectWriter ow = uk.gov.pay.adminusers.resources.HealthCheckResourceTest.objectMapper.writer().withDefaultPrettyPrinter();
        java.lang.String body = ow.writeValueAsString(response.getEntity());
        com.jayway.jsonassert.JsonAssert.with(body).assertThat("$.*", org.hamcrest.Matchers.hasSize(2)).assertThat("$.ping.healthy", org.hamcrest.core.Is.is(true)).assertThat("$.deadlocks.healthy", org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    void checkHealthCheck_pingIsHealthy_deadlocksIsUnhealthy() throws com.fasterxml.jackson.core.JsonProcessingException {
        java.util.SortedMap<java.lang.String, com.codahale.metrics.health.HealthCheck.Result> map = new java.util.TreeMap<>();
        map.put("ping", com.codahale.metrics.health.HealthCheck.Result.healthy());
        map.put("deadlocks", com.codahale.metrics.health.HealthCheck.Result.unhealthy("no new threads available"));
        org.mockito.Mockito.when(healthCheckRegistry.runHealthChecks()).thenReturn(map);
        javax.ws.rs.core.Response response = resource.healthCheck();
        org.hamcrest.MatcherAssert.assertThat(response.getStatus(), org.hamcrest.core.Is.is(503));
        com.fasterxml.jackson.databind.ObjectWriter ow = uk.gov.pay.adminusers.resources.HealthCheckResourceTest.objectMapper.writer().withDefaultPrettyPrinter();
        java.lang.String body = ow.writeValueAsString(response.getEntity());
        com.jayway.jsonassert.JsonAssert.with(body).assertThat("$.*", org.hamcrest.Matchers.hasSize(2)).assertThat("$.ping.healthy", org.hamcrest.core.Is.is(true)).assertThat("$.deadlocks.healthy", org.hamcrest.core.Is.is(false));
    }
}
