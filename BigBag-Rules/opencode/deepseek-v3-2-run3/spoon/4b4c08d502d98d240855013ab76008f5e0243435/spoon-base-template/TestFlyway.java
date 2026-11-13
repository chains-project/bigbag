import org.flywaydb.core.Flyway;

public class TestFlyway {
    public void testMethod() {
        Flyway flyway = new Flyway();
        flyway.setDataSource("jdbc:h2:mem:test", "user", "password");
        flyway.setLocations("db/migration");
        flyway.migrate();
    }
}