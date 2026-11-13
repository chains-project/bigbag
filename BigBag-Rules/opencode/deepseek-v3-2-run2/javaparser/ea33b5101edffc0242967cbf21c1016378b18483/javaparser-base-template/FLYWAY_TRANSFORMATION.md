# Flyway 9.x API Migration Transformation

## Overview
This JavaParser-based transformation automatically migrates Flyway API usage from version 8.x (and earlier) to version 9.x. The breaking change in Flyway 9.x replaces the old constructor+setter pattern with a new fluent API.

## Breaking Change Pattern

### Old API (Flyway 8.x and earlier)
```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setClassLoader(classLoader);
flyway.setLocations("db/migration");
flyway.setValidateOnMigrate(true);
```

### New API (Flyway 9.x)
```java
Flyway flyway = Flyway.configure(classLoader)
    .dataSource(dataSource)
    .locations("db/migration")
    .validateOnMigrate(true)
    .load();
```

## Transformation Rules

1. **Constructor Replacement**: `new Flyway()` → `Flyway.configure()`
2. **ClassLoader Handling**: `setClassLoader(classLoader)` becomes a parameter to `configure()`: `Flyway.configure(classLoader)`
3. **Setter Conversion**: Other `setXxx(value)` calls become fluent method chains: `.xxx(value)`
4. **Load Method**: Always adds `.load()` at the end of the chain
5. **Statement Cleanup**: Removes the original setter statements after transformation

## Supported Setters

The transformation handles all common Flyway setters including:
- `setDataSource` → `dataSource`
- `setLocations` → `locations`
- `setValidateOnMigrate` → `validateOnMigrate`
- `setBaselineDescription` → `baselineDescription`
- `setBaselineOnMigrate` → `baselineOnMigrate`
- `setBaselineVersion` → `baselineVersion`
- `setCallbacks` → `callbacks`
- And many more (see full list in Main.java)

## Usage

```bash
cd javaparser-base-template
mvn compile
java -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" github.chains.Main /path/to/your/project
```

## Example Output

**Input:**
```java
@Bean
public Flyway flyway() {
    Flyway flyway = new Flyway();
    flyway.setDataSource(dataSource());
    flyway.setClassLoader(getClass().getClassLoader());
    flyway.setLocations("db/migration");
    return flyway;
}
```

**Output:**
```java
@Bean
public Flyway flyway() {
    Flyway flyway = Flyway.configure(getClass().getClassLoader())
        .dataSource(dataSource())
        .locations("db/migration")
        .load();
    return flyway;
}
```

## Generic and Reusable

This transformation is:
- **Generic**: Uses pattern matching, not hardcoded project names
- **Reusable**: Can be applied to any Java project affected by Flyway 9.x
- **Conservative**: Only transforms code that follows the exact old pattern
- **Safe**: Preserves all other code unchanged

## Limitations

1. Only transforms inline Flyway declarations within the same block
2. Assumes setter calls immediately follow the declaration
3. Doesn't handle Flyway instances passed as parameters or returned from methods
4. Requires JavaParser 3.27.1 or compatible

## Testing

The transformation was successfully tested on the NEM project, fixing compilation errors caused by the Flyway 9.15.2 update.