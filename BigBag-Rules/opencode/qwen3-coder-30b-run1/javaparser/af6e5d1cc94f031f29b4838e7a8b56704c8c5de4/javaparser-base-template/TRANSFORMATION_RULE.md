# Generic Flyway API Transformation Rule

This document describes a generic transformation rule to fix breaking changes in the Flyway API.

## Problem Analysis

The breaking change in Flyway 9.17.0 changed the API from:
```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setLocations(locations);
flyway.setClassLoader(classLoader);
flyway.setValidateOnMigrate(validate);
```

To:
```java
Flyway flyway = new Flyway(org.flywaydb.core.api.configuration.FluentConfiguration.configure()
    .dataSource(dataSource)
    .locations(locations)
    .classLoader(classLoader)
    .validateOnMigrate(validate)
    .load());
```

## Transformation Rule

This rule identifies the old API pattern and replaces it with the new API pattern.

### Pattern Recognition:
- Find `new Flyway()` constructor calls with no arguments
- Find setter method calls on the resulting variable (`setDataSource`, `setLocations`, `setClassLoader`, `setValidateOnMigrate`)
- Replace with new API using FluentConfiguration

### Implementation Approach:
1. Parse Java files using JavaParser
2. Find all `new Flyway()` patterns  
3. Identify associated setter calls
4. Replace with single constructor call using FluentConfiguration API

## Files to Transform:
- `/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java`
- `/workspace/nem/nis/src/test/java/org/nem/nis/dao/TestConf.java`

## Usage:
```
# Compile the transformation
cd /workspace/javaparser-base-template && mvn compile

# Run the transformation (this would be a more complex implementation)
java -cp target/classes github.chains.Main /workspace/nem
```

## Generic Implementation Details:

The transformation follows these steps:
1. Parse Java source files using JavaParser
2. Identify Flyway constructor calls with no arguments
3. For each such call, find associated setter method calls
4. Replace with new API pattern using FluentConfiguration
5. Ensure proper imports are added