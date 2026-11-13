# Generic Flyway API Transformation Rule

## Overview
This JavaParser-based transformation rule automatically migrates Flyway API usage from pre-9.x versions to Flyway 9.18.0+.

## Breaking Change Pattern

### Old API (pre-9.x)
```java
final Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setClassLoader(classLoader);
flyway.setLocations(locations);
flyway.setValidateOnMigrate(validate);
```

### New API (9.18.0+)
```java
final Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .locations(locations)
    .validateOnMigrate(validate)
    .load();
```

Note: `setClassLoader()` is replaced by passing the class loader to `configure(ClassLoader)`.

## Transformation Details

### Method Mappings
| Old Method | New Method | Notes |
|------------|------------|-------|
| `new Flyway()` | `Flyway.configure()` | Constructor replaced with factory method |
| `setDataSource()` | `dataSource()` | Same parameters |
| `setLocations()` | `locations()` | Now accepts varargs |
| `setValidateOnMigrate()` | `validateOnMigrate()` | Same parameters |
| `setClassLoader()` | `configure(classLoader)` | Passed to configure() method |

### Key Features
1. **Generic**: Works on any Java project with Flyway API usage
2. **Pattern-based**: Matches structural patterns, not specific class names
3. **Safe**: Only transforms files containing Flyway usage
4. **Complete**: Removes old setter statements after transformation

## Usage

```bash
cd /workspace/javaparser-base-template
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" github.chains.Main /path/to/source
```

## Example Output
```
Transforming Flyway API usage in: /workspace/nem
Transformed: /workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java
Transformed: /workspace/nem/nis/src/test/java/org/nem/nis/dao/TestConf.java
Transformation complete.
```

## Implementation Notes

The transformation:
1. Scans Java files for `new Flyway()` or `new org.flywaydb.core.Flyway()`
2. Tracks associated setter method calls (`setDataSource`, `setClassLoader`, etc.)
3. Replaces constructor with `Flyway.configure()` chain
4. Maps setters to fluent configuration methods
5. Removes old setter statements
6. Handles both simple and fully-qualified class names

## Extensibility

To handle additional Flyway API changes:
1. Add method mappings in `mapSetterToFluentMethod()`
2. Update pattern matching in visitor methods
3. Extend the transformation logic as needed

This rule serves as a template for automating dependency migration tasks across multiple projects.