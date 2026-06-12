package uk.gov.pay.adminusers.persistence.dao;
public class DaoTestBase {
    private static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.class);

    public static uk.gov.service.payments.commons.testing.db.PostgresDockerExtension postgres = new uk.gov.service.payments.commons.testing.db.PostgresDockerExtension();

    protected static uk.gov.pay.adminusers.utils.DatabaseTestHelper databaseHelper;

    protected static uk.gov.pay.adminusers.infra.GuicedTestEnvironment env;

    @org.junit.jupiter.api.BeforeAll
    public static void setup() throws java.lang.Exception {
        final java.util.Properties properties = new java.util.Properties();
        properties.put("javax.persistence.jdbc.driver", uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getDriverClass());
        properties.put("javax.persistence.jdbc.url", uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getConnectionUrl());
        properties.put("javax.persistence.jdbc.user", uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getUsername());
        properties.put("javax.persistence.jdbc.password", uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getPassword());
        com.google.inject.persist.jpa.JpaPersistModule jpaModule = new com.google.inject.persist.jpa.JpaPersistModule("AdminUsersUnit").properties(properties);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper = new uk.gov.pay.adminusers.utils.DatabaseTestHelper(org.jdbi.v3.core.Jdbi.create(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getConnectionUrl(), uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getUsername(), uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getPassword()));
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getConnectionUrl(), uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getUsername(), uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getPassword())) {
            liquibase.Liquibase migrator = new liquibase.Liquibase("config/initial-db-state.xml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection));
            liquibase.Liquibase migrator2 = new liquibase.Liquibase("migrations.xml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection));
            migrator.update("");
            migrator2.update("");
        }
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env = uk.gov.pay.adminusers.infra.GuicedTestEnvironment.from(jpaModule).start();
    }

    @org.junit.jupiter.api.AfterAll
    public static void cleanUp() {
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getConnectionUrl(), uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getUsername(), uk.gov.pay.adminusers.persistence.dao.DaoTestBase.postgres.getPassword())) {
            liquibase.Liquibase migrator = new liquibase.Liquibase("config/initial-db-state.xml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection));
            liquibase.Liquibase migrator2 = new liquibase.Liquibase("migrations.xml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection));
            migrator2.dropAll();
            migrator.dropAll();
        } catch (java.lang.Exception e) {
            uk.gov.pay.adminusers.persistence.dao.DaoTestBase.logger.error("Error stopping docker", e);
        }
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.stop();
    }

    protected uk.gov.pay.adminusers.model.Permission aPermission() {
        return uk.gov.pay.adminusers.model.Permission.permission(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), "permission-name-" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "permission-description" + uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
    }
}
