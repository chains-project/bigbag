# Flyway API Transformation Rule

This Spoon transformation fixes the breaking changes in Flyway API from pre-9.21.0 to 9.21.0+.

## Breaking Change Analysis

**Old API pattern (pre-9.21.0):**
```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setClassLoader(classLoader);
flyway.setLocations(locations);
flyway.setValidateOnMigrate(validate);
// other setter calls
```

**New API pattern (9.21.0+):**
```java
Flyway flyway = Flyway.configure(classLoader)
        .dataSource(dataSource)
        .locations(locations)
        .validateOnMigrate(validate)
        // other fluent calls
        .load();
```

## Transformation Strategy

The transformation:
1. Finds all `new Flyway()` constructor calls
2. Collects subsequent setter method calls on the same variable
3. Transforms them into a fluent API chain
4. Handles special case: `setClassLoader()` becomes argument to `Flyway.configure()`
5. Removes the old setter calls

## Generic Transformation Rule

The rule is parameterized by:
- Target class: `org.flywaydb.core.Flyway`
- Setter method patterns: `setDataSource`, `setClassLoader`, `setLocations`, `setValidateOnMigrate`
- Fluent method mappings:
  - `setDataSource` → `.dataSource()`
  - `setClassLoader` → argument to `Flyway.configure()`
  - `setLocations` → `.locations()`
  - `setValidateOnMigrate` → `.validateOnMigrate()`

## Usage

```bash
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:$(cat classpath.txt)" \
     github.chains.Main /path/to/source/code
```

## Tested On

Successfully tested on the @nem/ project which had compilation errors:
- `/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java`
- `/workspace/nem/nis/src/test/java/org/nem/nis/dao/TestConf.java`

## Limitations

- Requires Spoon to have access to project dependencies
- Assumes setter calls immediately follow constructor
- May not handle all edge cases (field assignments, method chains, etc.)