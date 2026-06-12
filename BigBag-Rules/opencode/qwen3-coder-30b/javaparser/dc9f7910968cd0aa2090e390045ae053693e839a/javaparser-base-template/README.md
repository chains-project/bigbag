/**
 * Flyway API Migration Rule for Maven Projects
 * 
 * This rule addresses the breaking API change in Flyway 9.x where:
 * 1. The parameterless constructor `new Flyway()` is no longer available
 * 2. Setter methods like `setDataSource()`, `setLocations()` are removed
 * 
 * Migration Pattern:
 * OLD:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(dataSource);
 *   flyway.setLocations(locations);
 *   flyway.setValidateOnMigrate(validate);
 * 
 * NEW:
 *   Flyway flyway = Flyway.configure()
 *       .dataSource(dataSource)
 *       .locations(locations)
 *       .validateOnMigrate(validate)
 *       .load();
 * 
 * How to use:
 * 1. Apply to any Maven project with Flyway 9.x
 * 2. Replace all occurrences of old Flyway API patterns
 * 3. No project-specific identifiers - generic rule
 */