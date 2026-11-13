# Generic Flyway API Migration Transformation Rule

## Problem Statement
A breaking change in Flyway Core (version 9.18.0) modified the `Flyway` constructor from:
```java
public Flyway()
```
to:
```java
public Flyway(org.flywaydb.core.api.configuration.Configuration p0)
```

This causes compilation errors in any project that instantiates `Flyway` without parameters.

## Generic Solution Pattern

### Transformation Rule
Replace all occurances of:
```java
new Flyway()
```

With:
```java
new Flyway(org.flywaydb.core.api.configuration.FluentConfiguration.configure())
```

### Implementation Approach (JavaParser-based)

1. **Pattern Matching**: 
   - Locate all `ObjectCreationExpr` nodes with type `Flyway` from package `org.flywaydb.core`
   - Filter constructors with zero arguments

2. **Transformation**:
   - Replace the constructor call `new Flyway()` with `new Flyway(FluentConfiguration.configure())`
   - Import required packages if not already present

### Structure of the Generic Transformation

The transformation follows this pattern:

```
Input: new Flyway()
Output: new Flyway(org.flywaydb.core.api.configuration.FluentConfiguration.configure())
```

### Parameters for Reusability

1. **Fully Qualified Type Names**: 
   - `org.flywaydb.core.Flyway` (target class)
   - `org.flywaydb.core.api.configuration.FluentConfiguration` (replacement constructor arg)

2. **Method Signatures**:
   - Constructor: `new Flyway(org.flywaydb.core.api.configuration.Configuration p0)`
   - Configuration factory: `FluentConfiguration.configure()`

### Usage Example

```bash
java -cp target/classes github.chains.Main /path/to/target/project
```

This transformation rule can be applied to any Maven project experiencing this exact same breaking change without modification.