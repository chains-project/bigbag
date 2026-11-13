# Flyway 9.21.1 Generic Constructor Fix

This is a generic JavaParser-based transformation rule that fixes Flyway constructor calls when upgrading from versions that supported `new Flyway()` to Flyway 9.21.1 which requires `new Flyway(Configuration)`.

## Problem

Flyway 9.21.1 removed the no-argument constructor `new Flyway()` and now requires a `Configuration` parameter.

## Solution

This transformation automatically:
1. Identifies all instances of `new Flyway()` constructor calls
2. Replaces them with `new Flyway(FluentConfiguration.configure())`
3. Adds the necessary import for `org.flywaydb.core.api.configuration.FluentConfiguration`

## Usage

```bash
# Build the project
mvn clean package

# Run the transformation on a source directory
java -jar target/javaparser-1.0-SNAPSHOT.jar /path/to/your/java/source
```

## How It Works

The transformation uses JavaParser to:
1. Parse Java source files
2. Find all `new Flyway()` object creation expressions
3. Replace them with `new Flyway(FluentConfiguration.configure())`
4. Add the required import statement if missing

## Files Modified

This tool will modify Java source files by replacing:
```java
// Before
new Flyway()

// After  
new Flyway(FluentConfiguration.configure())
```

## Reusability

This is a generic solution that can be applied to any Maven project that needs to upgrade Flyway from an older version to 9.21.1 or later. It handles both fully-qualified names (`org.flywaydb.core.Flyway`) and simple names (`Flyway`) correctly.

## Limitations

This transformation focuses specifically on the constructor change and does not handle method calls on Flyway instances (like `flyway.setDataSource(...)`). For more complex scenarios, additional transformations may be needed.