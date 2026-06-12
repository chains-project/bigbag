package uk.gov.pay.adminusers.app;
public class AdminUsersApp extends io.dropwizard.Application<uk.gov.pay.adminusers.app.config.AdminUsersConfig> {
    private static final boolean NON_STRICT_VARIABLE_SUBSTITUTOR = false;

    private static final java.lang.String SERVICE_METRICS_NODE = "adminusers";

    private static final int GRAPHITE_SENDING_PERIOD_SECONDS = 10;

    @java.lang.Override
    public void initialize(io.dropwizard.setup.Bootstrap<uk.gov.pay.adminusers.app.config.AdminUsersConfig> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new io.dropwizard.configuration.SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new io.dropwizard.configuration.EnvironmentVariableSubstitutor(uk.gov.pay.adminusers.app.AdminUsersApp.NON_STRICT_VARIABLE_SUBSTITUTOR)));
        bootstrap.addBundle(new io.dropwizard.migrations.MigrationsBundle<>() {
            @java.lang.Override
            public io.dropwizard.db.DataSourceFactory getDataSourceFactory(uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration) {
                return configuration.getDataSourceFactory();
            }
        });
        bootstrap.addCommand(new uk.gov.pay.adminusers.app.healthchecks.DependentResourceWaitCommand());
        bootstrap.addCommand(new uk.gov.pay.adminusers.app.healthchecks.MigrateToInitialDbState());
        bootstrap.getObjectMapper().getSubtypeResolver().registerSubtypes(uk.gov.service.payments.logging.LogstashConsoleAppenderFactory.class);
        bootstrap.getObjectMapper().getSubtypeResolver().registerSubtypes(uk.gov.service.payments.logging.GovUkPayDropwizardRequestJsonLogLayoutFactory.class);
    }

    @java.lang.Override
    public void run(uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration, io.dropwizard.setup.Environment environment) {
        final com.google.inject.Injector injector = com.google.inject.Guice.createInjector(new uk.gov.pay.adminusers.app.config.AdminUsersModule(configuration, environment));
        injector.getInstance(uk.gov.pay.adminusers.app.config.PersistenceServiceInitialiser.class);
        initialiseMetrics(configuration, environment);
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.filters.LoggingMDCRequestFilter.class));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.filters.LoggingMDCResponseFilter.class));
        environment.servlets().addFilter("LoggingFilter", new uk.gov.service.payments.logging.LoggingFilter()).addMappingForUrlPatterns(java.util.EnumSet.of(javax.servlet.DispatcherType.REQUEST), true, "/v1/*");
        environment.healthChecks().register("database", new uk.gov.service.payments.commons.utils.healthchecks.DatabaseHealthCheck(configuration.getDataSourceFactory()));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.resources.UserResource.class));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.resources.ServiceResource.class));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.resources.ToolboxEndpointResource.class));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.resources.ForgottenPasswordResource.class));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.resources.InviteResource.class));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.resources.ResetPasswordResource.class));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.resources.HealthCheckResource.class));
        environment.jersey().register(injector.getInstance(uk.gov.pay.adminusers.resources.EmailResource.class));
        // Register the custom ExceptionMapper(s)
        environment.jersey().register(new uk.gov.pay.adminusers.exception.ValidationExceptionMapper());
        environment.jersey().register(new uk.gov.pay.adminusers.exception.NotFoundExceptionMapper());
        environment.jersey().register(new uk.gov.pay.adminusers.resources.InvalidEmailRequestExceptionMapper());
        environment.jersey().register(new uk.gov.pay.adminusers.resources.InvalidMerchantDetailsExceptionMapper());
        environment.jersey().register(new uk.gov.pay.adminusers.exception.ConflictExceptionMapper());
        environment.lifecycle().manage(injector.getInstance(uk.gov.pay.adminusers.queue.managed.EventSubscriberQueueMessageReceiver.class));
    }

    private void initialiseMetrics(uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration, io.dropwizard.setup.Environment environment) {
        uk.gov.service.payments.commons.utils.metrics.DatabaseMetricsService metricsService = new uk.gov.service.payments.commons.utils.metrics.DatabaseMetricsService(configuration.getDataSourceFactory(), environment.metrics(), "adminusers");
        environment.lifecycle().scheduledExecutorService("metricscollector").threads(1).build().scheduleAtFixedRate(metricsService::updateMetricData, 0, uk.gov.pay.adminusers.app.AdminUsersApp.GRAPHITE_SENDING_PERIOD_SECONDS / 2, java.util.concurrent.TimeUnit.SECONDS);
        com.codahale.metrics.graphite.GraphiteSender graphiteUDP = new com.codahale.metrics.graphite.GraphiteUDP(configuration.getGraphiteHost(), configuration.getGraphitePort());
        com.codahale.metrics.graphite.GraphiteReporter.forRegistry(environment.metrics()).prefixedWith(uk.gov.pay.adminusers.app.AdminUsersApp.SERVICE_METRICS_NODE).build(graphiteUDP).start(uk.gov.pay.adminusers.app.AdminUsersApp.GRAPHITE_SENDING_PERIOD_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    public static void main(java.lang.String[] args) throws java.lang.Exception {
        new uk.gov.pay.adminusers.app.AdminUsersApp().run(args);
    }
}
