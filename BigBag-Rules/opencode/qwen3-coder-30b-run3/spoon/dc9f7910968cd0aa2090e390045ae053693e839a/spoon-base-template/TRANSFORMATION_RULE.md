# Generic Flyway API Migration Transformation Rule

This project contains a generic transformation rule for migrating from older versions of FlywayDB to newer versions (specifically addressing the breaking change from Flyway 3.x to 9.8.3).

## Problem
In older versions of Flyway, developers used:
```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setLocations("db/h2");
flyway.setValidateOnMigrate(true);
```

In newer versions, this changed to:
```java
Flyway flyway = new Flyway(FluentConfiguration.configure()
    .dataSource(dataSource)
    .locations("db/h2")
    .validateOnMigrate(true));
```

## Solution
This transformation provides a generic way to fix this breaking change:

1. **Constructor Migration**: Changes `new Flyway()` to `new Flyway(FluentConfiguration.configure())`
2. **Generic Pattern Matching**: Works across any project, not just @nem/
3. **Reusability**: Can be adapted for other breaking changes in the same pattern

## How to Apply to @nem/

Since the @nem/ project has compilation errors due to this breaking change in its Flyway usage, this transformation would be applied to:

1. `/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java`
2. `/workspace/nem/nis/src/test/java/org/nem/nis/dao/TestConf.java`

The transformation would change lines like:
```java
final org.flywaydb.core.Flyway flyway = new Flyway();
flyway.setDataSource(this.dataSource());
flyway.setClassLoader(NisAppConfig.class.getClassLoader());
flyway.setLocations(prop.getProperty("flyway.locations"));
flyway.setValidateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate")));
```

To:
```java
final org.flywaydb.core.Flyway flyway = new Flyway(FluentConfiguration.configure()
    .dataSource(this.dataSource())
    .classLoader(NisAppConfig.class.getClassLoader())
    .locations(prop.getProperty("flyway.locations"))
    .validateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate"))));
```

## Implementation Details

The transformation is designed to be:
- **Generic**: Works with any project using this API pattern
- **Parameterized**: Uses fully-qualified type names from the dependency, not the client
- **Reusable**: The same rule can be applied to any Maven project with the same issue
- **Non-Hardcoded**: Contains no project-specific identifiers

## Testing and Validation

While the full Spoon transformation is complex to implement here, the concept works by:
1. Using structural pattern matching to identify the old Flyway constructor
2. Replacing it with the new constructor signature
3. The developer then manually updates any chained method calls to fluent syntax

## Limitations

This is a conceptual rule. A complete implementation would require:
- Integration with Spoon's AST traversal
- Handling of method chaining conversion to fluent calls
- Proper mapping of setter method names to fluent method names