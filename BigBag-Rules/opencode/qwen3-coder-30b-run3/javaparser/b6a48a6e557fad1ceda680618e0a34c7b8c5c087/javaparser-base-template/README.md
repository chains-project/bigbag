# Flyway API Migration Transformation

This project contains a generic transformation rule to fix Flyway API breaking changes in Maven projects.

## Problem
Flyway Core 9.18.0 introduced a breaking API change where the `Flyway` constructor changed from:
```java
public Flyway()
```
to:
```java
public Flyway(org.flywaydb.core.api.configuration.Configuration p0)
```

## Solution
This transformation replaces all instances of:
```java
new Flyway()
```
with:
```java
new Flyway(org.flywaydb.core.api.configuration.FluentConfiguration.configure())
```

## Usage
1. Build the project:
```bash
mvn clean install
```

2. Run the transformation on any Maven project:
```bash
java -cp target/classes github.chains.Main /path/to/target/project
```

## Generic Transformation Rule
See TRANSFORMATION_RULE.md for the detailed generic transformation approach.