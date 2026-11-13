package uk.gov.pay.adminusers.infra;
public class AppWithPostgresAndSqsRule implements org.junit.rules.TestRule {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.infra.AppWithPostgresAndSqsRule.class);

    private static final java.lang.String JPA_UNIT = "AdminUsersUnit";

    private final java.lang.String configFilePath;

    private final uk.gov.service.payments.commons.testing.db.PostgresDockerRule postgres;

    private com.amazonaws.services.sqs.AmazonSQS sqsClient;

    private final io.dropwizard.testing.junit.DropwizardAppRule<uk.gov.pay.adminusers.app.config.AdminUsersConfig> app;

    private final org.junit.rules.RuleChain rules;

    private uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper;

    private int wireMockPort = uk.gov.service.payments.commons.testing.port.PortFactory.findFreePort();

    public AppWithPostgresAndSqsRule(io.dropwizard.testing.ConfigOverride... configOverrides) {
        this("config/test-it-config.yaml", configOverrides);
    }

    public AppWithPostgresAndSqsRule(java.lang.String configPath, io.dropwizard.testing.ConfigOverride... configOverrides) {
        configFilePath = io.dropwizard.testing.ResourceHelpers.resourceFilePath(configPath);
        postgres = new uk.gov.service.payments.commons.testing.db.PostgresDockerRule();
        sqsClient = uk.gov.pay.adminusers.infra.SqsTestDocker.initialise(java.util.Collections.singletonList("event-queue"));
        io.dropwizard.testing.ConfigOverride[] newConfigOverrides = java.util.List.of(io.dropwizard.testing.ConfigOverride.config("database.url", postgres.getConnectionUrl()), io.dropwizard.testing.ConfigOverride.config("database.user", postgres.getUsername()), io.dropwizard.testing.ConfigOverride.config("database.password", postgres.getPassword())).toArray(new io.dropwizard.testing.ConfigOverride[0]);
        newConfigOverrides = overrideSqsConfig(newConfigOverrides);
        newConfigOverrides = overrideUrlsConfig(newConfigOverrides);
        app = new io.dropwizard.testing.junit.DropwizardAppRule<>(uk.gov.pay.adminusers.app.AdminUsersApp.class, configFilePath, org.apache.commons.lang3.ArrayUtils.addAll(newConfigOverrides, configOverrides));
        createJpaModule(postgres);
        rules = org.junit.rules.RuleChain.outerRule(postgres).around(app);
        registerShutdownHook();
    }

    @java.lang.Override
    public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runner.Description description) {
        return rules.apply(new org.junit.runners.model.Statement() {
            @java.lang.Override
            public void evaluate() throws java.lang.Throwable {
                uk.gov.pay.adminusers.infra.AppWithPostgresAndSqsRule.LOGGER.info("Clearing database.");
                app.getApplication().run("db", "drop-all", "--confirm-delete-everything", configFilePath);
                app.getApplication().run("migrateToInitialDbState", configFilePath);
                doSecondaryDatabaseMigration();
                restoreDropwizardsLogging();
                io.dropwizard.db.DataSourceFactory dataSourceFactory = app.getConfiguration().getDataSourceFactory();
                databaseTestHelper = new uk.gov.pay.adminusers.utils.DatabaseTestHelper(org.jdbi.v3.core.Jdbi.create(dataSourceFactory.getUrl(), dataSourceFactory.getUser(), dataSourceFactory.getPassword()));
                base.evaluate();
            }
        }, description);
    }

    private void doSecondaryDatabaseMigration() throws java.sql.SQLException, liquibase.exception.LiquibaseException {
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword())) {
            liquibase.Liquibase migrator = new liquibase.Liquibase("migrations.xml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection));
            migrator.update("");
        }
    }

    public int getLocalPort() {
        return app.getLocalPort();
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    public com.amazonaws.services.sqs.AmazonSQS getSqsClient() {
        return sqsClient;
    }

    public int getWireMockPort() {
        return wireMockPort;
    }

    private void registerShutdownHook() {
        java.lang.Runtime.getRuntime().addShutdownHook(new java.lang.Thread(postgres::stop));
    }

    private com.google.inject.persist.jpa.JpaPersistModule createJpaModule(final uk.gov.service.payments.commons.testing.db.PostgresDockerRule postgres) {
        final java.util.Properties properties = new java.util.Properties();
        properties.put("javax.persistence.jdbc.driver", postgres.getDriverClass());
        properties.put("javax.persistence.jdbc.url", postgres.getConnectionUrl());
        properties.put("javax.persistence.jdbc.user", postgres.getUsername());
        properties.put("javax.persistence.jdbc.password", postgres.getPassword());
        final com.google.inject.persist.jpa.JpaPersistModule jpaModule = new com.google.inject.persist.jpa.JpaPersistModule(uk.gov.pay.adminusers.infra.AppWithPostgresAndSqsRule.JPA_UNIT);
        jpaModule.properties(properties);
        return jpaModule;
    }

    private void restoreDropwizardsLogging() {
        app.getConfiguration().getLoggingFactory().configure(app.getEnvironment().metrics(), app.getApplication().getName());
    }

    private io.dropwizard.testing.ConfigOverride[] overrideSqsConfig(io.dropwizard.testing.ConfigOverride[] configOverrides) {
        java.util.List<io.dropwizard.testing.ConfigOverride> newConfigOverride = com.google.common.collect.Lists.newArrayList(configOverrides);
        newConfigOverride.add(io.dropwizard.testing.ConfigOverride.config("sqs.eventSubscriberQueueUrl", uk.gov.pay.adminusers.infra.SqsTestDocker.getQueueUrl("event-queue")));
        newConfigOverride.add(io.dropwizard.testing.ConfigOverride.config("sqs.endpoint", uk.gov.pay.adminusers.infra.SqsTestDocker.getEndpoint()));
        return newConfigOverride.toArray(new io.dropwizard.testing.ConfigOverride[0]);
    }

    private io.dropwizard.testing.ConfigOverride[] overrideUrlsConfig(io.dropwizard.testing.ConfigOverride[] configOverrides) {
        java.util.List<io.dropwizard.testing.ConfigOverride> newConfigOverride = com.google.common.collect.Lists.newArrayList(configOverrides);
        newConfigOverride.add(io.dropwizard.testing.ConfigOverride.config("notify.notificationBaseURL", "http://localhost:" + wireMockPort));
        newConfigOverride.add(io.dropwizard.testing.ConfigOverride.config("ledgerBaseURL", "http://localhost:" + wireMockPort));
        return newConfigOverride.toArray(new io.dropwizard.testing.ConfigOverride[0]);
    }
}
