package uk.gov.pay.adminusers.infra;
public class AppWithPostgresExtension implements org.junit.jupiter.api.extension.BeforeAllCallback {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.infra.AppWithPostgresExtension.class);

    private static final java.lang.String JPA_UNIT = "AdminUsersUnit";

    private final java.lang.String configFilePath;

    private final uk.gov.service.payments.commons.testing.db.PostgresDockerExtension postgres;

    private final io.dropwizard.testing.junit5.DropwizardAppExtension<uk.gov.pay.adminusers.app.config.AdminUsersConfig> app;

    private uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseTestHelper;

    public AppWithPostgresExtension(io.dropwizard.testing.ConfigOverride... configOverrides) {
        this("config/test-it-config.yaml", configOverrides);
    }

    public AppWithPostgresExtension(java.lang.String configPath, io.dropwizard.testing.ConfigOverride... configOverrides) {
        configFilePath = io.dropwizard.testing.ResourceHelpers.resourceFilePath(configPath);
        postgres = new uk.gov.service.payments.commons.testing.db.PostgresDockerExtension();
        io.dropwizard.testing.ConfigOverride[] postgresOverrides = java.util.List.of(io.dropwizard.testing.ConfigOverride.config("database.url", postgres.getConnectionUrl()), io.dropwizard.testing.ConfigOverride.config("database.user", postgres.getUsername()), io.dropwizard.testing.ConfigOverride.config("database.password", postgres.getPassword())).toArray(new io.dropwizard.testing.ConfigOverride[0]);
        app = new io.dropwizard.testing.junit5.DropwizardAppExtension<>(uk.gov.pay.adminusers.app.AdminUsersApp.class, configFilePath, org.apache.commons.lang3.ArrayUtils.addAll(postgresOverrides, configOverrides));
        createJpaModule(postgres);
        registerShutdownHook();
        try {
            // starts dropwizard application. This is required as we don't use DropwizardExtensionsSupport (which starts application)
            // due to config overrides we need at runtime for database, sqs and any custom configuration needed for tests
            app.before();
        } catch (java.lang.Exception e) {
            uk.gov.pay.adminusers.infra.AppWithPostgresExtension.LOGGER.error("Exception starting application - {}", e.getMessage());
            throw new java.lang.RuntimeException(e);
        }
    }

    @java.lang.Override
    public void beforeAll(org.junit.jupiter.api.extension.ExtensionContext context) throws java.lang.Exception {
        uk.gov.pay.adminusers.infra.AppWithPostgresExtension.LOGGER.info("Clearing database.");
        app.getApplication().run("db", "drop-all", "--confirm-delete-everything", configFilePath);
        app.getApplication().run("migrateToInitialDbState", configFilePath);
        doSecondaryDatabaseMigration();
        restoreDropwizardsLogging();
        io.dropwizard.db.DataSourceFactory dataSourceFactory = app.getConfiguration().getDataSourceFactory();
        databaseTestHelper = new uk.gov.pay.adminusers.utils.DatabaseTestHelper(org.jdbi.v3.core.Jdbi.create(dataSourceFactory.getUrl(), dataSourceFactory.getUser(), dataSourceFactory.getPassword()));
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

    private void registerShutdownHook() {
        java.lang.Runtime.getRuntime().addShutdownHook(new java.lang.Thread(postgres::stop));
    }

    private com.google.inject.persist.jpa.JpaPersistModule createJpaModule(final uk.gov.service.payments.commons.testing.db.PostgresDockerExtension postgres) {
        final java.util.Properties properties = new java.util.Properties();
        properties.put("javax.persistence.jdbc.driver", postgres.getDriverClass());
        properties.put("javax.persistence.jdbc.url", postgres.getConnectionUrl());
        properties.put("javax.persistence.jdbc.user", postgres.getUsername());
        properties.put("javax.persistence.jdbc.password", postgres.getPassword());
        final com.google.inject.persist.jpa.JpaPersistModule jpaModule = new com.google.inject.persist.jpa.JpaPersistModule(uk.gov.pay.adminusers.infra.AppWithPostgresExtension.JPA_UNIT);
        jpaModule.properties(properties);
        return jpaModule;
    }

    private void restoreDropwizardsLogging() {
        app.getConfiguration().getLoggingFactory().configure(app.getEnvironment().metrics(), app.getApplication().getName());
    }
}
