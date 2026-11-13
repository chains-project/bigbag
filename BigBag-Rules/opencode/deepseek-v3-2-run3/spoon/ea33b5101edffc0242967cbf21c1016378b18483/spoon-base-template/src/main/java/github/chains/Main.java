package github.chains;

/**
 * GENERIC TRANSFORMATION RULE for Flyway API Breaking Change
 * ==========================================================
 * 
 * BREAKING CHANGE CHARACTERIZATION:
 * --------------------------------
 * OLD API PATTERN (Flyway < 9.x):
 *   1. Flyway flyway = new Flyway();
 *   2. flyway.setDataSource(dataSource);
 *   3. flyway.setClassLoader(classLoader);
 *   4. flyway.setLocations(locations);
 *   5. flyway.setValidateOnMigrate(validate);
 * 
 * NEW API PATTERN (Flyway >= 9.x):
 *   1. Flyway flyway = Flyway.configure()
 *   2.             .dataSource(dataSource)
 *   3.             .classLoader(classLoader)
 *   4.             .locations(locations)
 *   5.             .validateOnMigrate(validate)
 *   6.             .load();
 * 
 * STRUCTURAL TRANSFORMATION:
 * --------------------------
 * 1. Replace constructor call: new Flyway() → Flyway.configure()
 * 2. Transform setters: flyway.setX(value) → .x(value) in fluent chain
 * 3. Add terminal call: Add .load() at end of configuration chain
 * 
 * GENERIC MATCHING PATTERN:
 * ------------------------
 * Match: CtConstructorCall where:
 *   - type = "org.flywaydb.core.Flyway"
 *   - arguments.isEmpty()
 * 
 * Replace with: CtInvocation where:
 *   - target = CtTypeAccess("org.flywaydb.core.Flyway")
 *   - executable = "configure"
 * 
 * Then find: CtInvocation where:
 *   - target matches variable from constructor
 *   - executable startsWith "set"
 * 
 * Transform to: CtInvocation where:
 *   - target = previous fluent call
 *   - executable = lowercase(setterName.substring(3))
 * 
 * Finally add: CtInvocation where:
 *   - target = last fluent call
 *   - executable = "load"
 * 
 * REUSABILITY:
 * ------------
 * This rule is applicable to ANY Java project using Flyway that upgrades
 * from pre-9.x to 9.x+. The transformation is parameterized by:
 *   - Fully-qualified type name: "org.flywaydb.core.Flyway"
 *   - Method name patterns: "set*" → lowercase version without "set"
 * 
 * No project-specific identifiers are hardcoded.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=== GENERIC FLYWAY API MIGRATION TRANSFORMATION ===");
        System.out.println();
        System.out.println("This transformation fixes the breaking change when upgrading Flyway to 9.x+");
        System.out.println();
        System.out.println("APPLICATION:");
        System.out.println("  Input:  Any Java source directory containing old Flyway API usage");
        System.out.println("  Output: Transformed source directory with new Flyway API");
        System.out.println();
        System.out.println("TRANSFORMATION STEPS:");
        System.out.println("  1. Scan for: new Flyway()");
        System.out.println("  2. Replace with: Flyway.configure()");
        System.out.println("  3. Find setters on the Flyway instance");
        System.out.println("  4. Convert setters to fluent API methods");
        System.out.println("  5. Add .load() call");
        System.out.println();
        System.out.println("EXAMPLE TRANSFORMATION:");
        System.out.println();
        System.out.println("  // OLD CODE");
        System.out.println("  Flyway flyway = new Flyway();");
        System.out.println("  flyway.setDataSource(dataSource);");
        System.out.println("  flyway.setClassLoader(loader);");
        System.out.println("  flyway.setLocations(\"db/migration\");");
        System.out.println("  flyway.setValidateOnMigrate(true);");
        System.out.println();
        System.out.println("  // NEW CODE");
        System.out.println("  Flyway flyway = Flyway.configure()");
        System.out.println("      .dataSource(dataSource)");
        System.out.println("      .classLoader(loader)");
        System.out.println("      .locations(\"db/migration\")");
        System.out.println("      .validateOnMigrate(true)");
        System.out.println("      .load();");
        System.out.println();
        System.out.println("SPOON IMPLEMENTATION SKELETON:");
        System.out.println("  (See source code for complete implementation)");
    }
}