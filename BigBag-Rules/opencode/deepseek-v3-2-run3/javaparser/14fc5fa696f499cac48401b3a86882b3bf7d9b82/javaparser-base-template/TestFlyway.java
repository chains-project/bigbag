package test;

import org.flywaydb.core.Flyway;
import javax.sql.DataSource;

public class TestFlywayTransformation {

    // Test case 1: Simple Flyway initialization
    public Flyway createFlywayOld(DataSource ds) {
        Flyway flyway = new Flyway();
        flyway.setDataSource(ds);
        flyway.setLocations("db/migrations");
        flyway.setValidateOnMigrate(true);
        return Flyway.configure().dataSource(ds).locations("db/migrations").validateOnMigrate(true).load();
    }

    // Test case 2: Flyway with class loader
    public Flyway createFlywayWithClassLoader(DataSource ds, ClassLoader cl) {
        final Flyway flyway = new Flyway();
        flyway.setDataSource(ds);
        flyway.setClassLoader(cl);
        flyway.setLocations("db/h2");
        return Flyway.configure().dataSource(ds).locations("db/h2").load();
    }

    // Test case 3: Direct return
    public Flyway createFlywayDirect(DataSource ds) {
        return new Flyway();
    }
}
