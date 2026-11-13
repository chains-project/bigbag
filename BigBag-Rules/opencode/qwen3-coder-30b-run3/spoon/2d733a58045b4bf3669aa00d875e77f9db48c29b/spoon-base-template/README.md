# Flyway API Fix Transformer

A generic Spoon transformation tool that fixes breaking Flyway API changes in Maven projects.

## Problem

Flyway 9.11.0 introduced breaking changes to the API, specifically deprecating `setValidateOnMigrate()` in favor of `validateOnMigrate()`.

## Solution

This transformer detects and fixes Flyway API usage patterns in Java source code by:
1. Identifying deprecated `setValidateOnMigrate()` method calls
2. Replacing them with the new `validateOnMigrate()` method calls

## Usage

### Prerequisites

- Java 11+
- Maven 3+

### Building

```bash
mvn clean package
```

### Running

To transform a Maven project:

1. Place this project in the same directory as your Maven project
2. Run:
```bash
java -cp target/flyway-transformer-1.0-SNAPSHOT.jar github.chains.Main
```

Or run directly with Maven:
```bash
mvn exec:java -Dexec.mainClass="github.chains.Main"
```

## How It Works

The transformation works by:
1. Scanning all Java source files in the project
2. Detecting Flyway API usage patterns (specifically `setValidateOnMigrate`)
3. Automatically converting deprecated patterns to the new API

## Customization

To adapt this transformation for different projects:
1. Modify the input source directory in `Main.java`
2. Adjust the output directory in `Main.java`
3. Extend `FlywayFixTransformer` with additional fix patterns if needed

## Example Fix

**Before:**
```java
final org.flywaydb.core.Flyway flyway = new Flyway();
flyway.setDataSource(this.dataSource());
flyway.setValidateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate")));
```

**After:**
```java
final org.flywaydb.core.Flyway flyway = new Flyway();
flyway.setDataSource(this.dataSource());
flyway.validateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate")));
```