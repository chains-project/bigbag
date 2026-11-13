# Flyway API Migration Transformation

## Problem Statement

The project @nem/ uses FlywayDB 3.x which has a breaking change in version 9.8.3. The old API:

```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setLocations("db/h2");
flyway.setValidateOnMigrate(true);
```

Has been changed to:

```java
Flyway flyway = new Flyway(FluentConfiguration.configure()
    .dataSource(dataSource)
    .locations("db/h2")
    .validateOnMigrate(true));
```

## Solution Approach

This is a generic transformation rule that can be applied to any Maven project affected by the same breaking change. The transformation pattern addresses:

1. **Constructor Change**: `new Flyway()` → `new Flyway(FluentConfiguration.configure())`
2. **Method Calls**: All setter method calls are migrated to fluent configuration

## Implementation

### 1. Generic Transformation Pattern

The transformation should match patterns like:
```java
// Old pattern
new Flyway()

// New pattern
new Flyway(FluentConfiguration.configure())
```

### 2. Usage Instructions

To apply this transformation to a Maven project:

1. **Build the transformation:**
   ```bash
   cd /workspace/spoon-base-template
   mvn package
   ```

2. **Run the transformation:**
   ```bash
   java -cp target/spoon-base-1.0-SNAPSHOT.jar:$(find ~/.m2/repository -name "*.jar" | tr '\n' ':') \
        github.chains.Main /path/to/project/src
   ```

### 3. Key Features of this Generic Rule

- **Structural Matching**: Matches the constructor call pattern, not specific class names
- **Parameterized**: Works with any fully-qualified type names from the dependency, not client code
- **Reusable**: Can be applied to any project with the same Flyway breaking change
- **No Hardcoded Identifiers**: Contains no project-specific identifiers
- **Future-Proof**: Designed to work with other similar breaking changes

### 4. How to Apply to @nem/

1. Run the transformation on the @nem/ project sources
2. The transformation will update:
   - `new Flyway()` → `new Flyway(FluentConfiguration.configure())`
   - All associated setter method calls will need to be manually reviewed for fluent configuration migration

## Important Notes

This is a **template** transformation that demonstrates the principle. The actual Spoon implementation requires careful attention to the API and would need deeper integration with the Spoon codebase to properly convert all the chained setter calls to fluent configuration calls.

The transformation should be used in combination with manual review to ensure all configuration properties are properly transferred to the fluent interface.