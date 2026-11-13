package uk.gov.pay.adminusers.app.healthchecks;
public class MigrateToInitialDbState extends io.dropwizard.cli.ConfiguredCommand<uk.gov.pay.adminusers.app.config.AdminUsersConfig> {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.app.healthchecks.MigrateToInitialDbState.class);

    public MigrateToInitialDbState() {
        super("migrateToInitialDbState", "Migrate to initial (selfservice) database state, if necessary");
    }

    @java.lang.Override
    protected void run(io.dropwizard.setup.Bootstrap<uk.gov.pay.adminusers.app.config.AdminUsersConfig> bootstrap, net.sourceforge.argparse4j.inf.Namespace namespace, uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration) {
        try (java.sql.Connection connection = getDatabaseConnection(configuration);java.sql.PreparedStatement statement = connection.prepareStatement("select exists (select * from pg_tables where tablename='users')");java.sql.ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                boolean usersExists = resultSet.getBoolean(1);
                if (usersExists) {
                    uk.gov.pay.adminusers.app.healthchecks.MigrateToInitialDbState.LOGGER.info("Users table found in current environment. Not required for the initial database migration");
                } else {
                    uk.gov.pay.adminusers.app.healthchecks.MigrateToInitialDbState.LOGGER.info("Users table not found. Preparing for the initial database migration..");
                    performInitialMigration(connection);
                }
            }
        } catch (java.sql.SQLException e) {
            uk.gov.pay.adminusers.app.healthchecks.MigrateToInitialDbState.LOGGER.error("Error during initial DB setup", e);
            throw new java.lang.RuntimeException(e);
        }
    }

    private void performInitialMigration(java.sql.Connection connection) {
        try {
            liquibase.Liquibase migrator = new liquibase.Liquibase("config/initial-db-state.xml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection));
            migrator.update("");
        } catch (liquibase.exception.LiquibaseException e) {
            uk.gov.pay.adminusers.app.healthchecks.MigrateToInitialDbState.LOGGER.error("Error performing liquibase initial database migration", e);
            throw new java.lang.RuntimeException(e);
        }
    }

    private java.sql.Connection getDatabaseConnection(uk.gov.pay.adminusers.app.config.AdminUsersConfig configuration) throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(configuration.getDataSourceFactory().getUrl(), configuration.getDataSourceFactory().getUser(), configuration.getDataSourceFactory().getPassword());
    }
}
