# Flyway API Migration Tool

This tool fixes breaking changes in Flyway API when upgrading from version 8.x to 9.15.1.

## Problem

In Flyway 9.15.1, the `Flyway` constructor changed from:
```java
Flyway flyway = new Flyway();
```

To:
```java
Flyway flyway = new Flyway(Flyway.configure().load());
```

## Solution

This Spoon-based transformation automatically updates all occurrences of the old Flyway constructor pattern to use the new API.

## Usage

```bash
# Compile the tool
mvn compile

# Run the transformation on a directory
java -cp target/classes github.chains.Main /path/to/your/project/src
```

The tool will process all Java files and create a fixed version in `/path/to/your/project/src_fixed/`

## How it works

1. Finds all `new Flyway()` constructor calls
2. Replaces them with `new Flyway(Flyway.configure().load())`
3. Preserves all existing configuration method calls