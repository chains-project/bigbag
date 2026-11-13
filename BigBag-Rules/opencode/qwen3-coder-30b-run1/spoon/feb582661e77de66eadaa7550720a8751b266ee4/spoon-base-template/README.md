# Generic Liquibase API Fixer

This is a generic Spoon transformation tool that fixes breaking API changes in Liquibase projects when upgrading to version 4.8.0.

## Problem

When upgrading to Liquibase 4.8.0, projects using the ExecutorService API may encounter compilation errors due to breaking changes in the API. The old pattern:
```java
ExecutorService.getInstance().getExecutor(database).execute(statement)
```
Needs to be transformed to the new pattern:
```java
ExecutorService.getInstance().execute(database, statement)
```

## Solution

This tool provides a generic Spoon transformation that:
1. Identifies the problematic API pattern in Java source code
2. Replaces it with the correct new API pattern
3. Works with any Maven project that has this issue
4. Is completely generic and reusable

## Usage

1. **Build the tool:**
   ```bash
   mvn clean compile
   ```

2. **Run the transformation:**
   ```bash
   java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) \
        github.chains.LiquibaseExecutorServiceFixerMain <source-directory> [output-directory]
   ```

## How It Works

The transformation processor:
- Scans Java source files for the pattern `ExecutorService.getInstance().getExecutor(database).execute(statement)`
- Identifies the correct API call chain by analyzing the AST
- Replaces it with the new API signature `ExecutorService.getInstance().execute(database, statement)`
- Preserves all other code unchanged

## Requirements

- Java 17 or higher
- Maven 3.6+
- Spoon 11.2.1

## Files Included

- `LiquibaseExecutorServiceFixer.java`: The Spoon processor that performs the transformation
- `LiquibaseExecutorServiceFixerMain.java`: Main class for running the transformation
- `pom.xml`: Maven configuration
- `README.md`: This documentation

## Example

**Before:**
```java
ExecutorService.getInstance().getExecutor(this).execute(new DropStoredProcedureStatement(this.getLiquibaseCatalogName(), this.getLiquibaseSchemaName()));
```

**After:**
```java
ExecutorService.getInstance().execute(this, new DropStoredProcedureStatement(this.getLiquibaseCatalogName(), this.getLiquibaseSchemaName()));
```

This generic solution can be applied to any Maven project that encounters the same breaking change in Liquibase 4.8.0.