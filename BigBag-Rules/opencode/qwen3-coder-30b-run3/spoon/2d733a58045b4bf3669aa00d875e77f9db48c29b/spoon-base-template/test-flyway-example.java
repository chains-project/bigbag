/**
 * Test file showing Flyway API transformation
 * 
 * This demonstrates the kind of code that would be transformed:
 * 
 * From:
 * flyway.setValidateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate")));
 * 
 * To:
 * flyway.validateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate")));
 */

public class FlywayExample {
    public void badCode() {
        // This pattern would cause compilation errors in Flyway 9.11.0+
        final org.flywaydb.core.Flyway flyway = new Flyway();
        flyway.setValidateOnMigrate(Boolean.TRUE); // Deprecated in 9.11.0
    }
    
    public void goodCode() {
        // This is the corrected pattern for Flyway 9.11.0+
        final org.flywaydb.core.Flyway flyway = new Flyway();
        flyway.validateOnMigrate(Boolean.TRUE); // Correct for 9.11.0+
    }
}