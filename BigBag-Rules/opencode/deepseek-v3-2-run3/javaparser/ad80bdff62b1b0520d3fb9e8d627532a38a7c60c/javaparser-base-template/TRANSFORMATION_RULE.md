# Generic Transformation Rule: Flyway API Breaking Change (v<9 → v>=9)

## Problem Statement
Projects using Flyway <9 experience compilation errors when upgrading to Flyway >=9 due to API breaking changes.

## Breaking Change Characterization

### Old API Pattern (Flyway <9)
```java
// Pattern 1: Constructor + setters
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setClassLoader(classLoader);
flyway.setLocations(locations);
flyway.setValidateOnMigrate(validate);
return flyway;

// Pattern 2: Constructor + chained setters  
Flyway flyway = new Flyway()
    .setDataSource(dataSource)
    .setClassLoader(classLoader)
    .setLocations(locations);
return flyway;
```

### New API Pattern (Flyway >=9)
```java
// Pattern 1: Fluent configuration with load()
return Flyway.configure()
    .dataSource(dataSource)
    .locations(locations)
    .validateOnMigrate(validate)
    .load();

// Pattern 2: With ClassLoader parameter
return Flyway.configure(classLoader)
    .dataSource(dataSource)
    .locations(locations)
    .load();
```

## Key API Changes
1. **Constructor**: `new Flyway()` → `Flyway.configure()` or `Flyway.configure(ClassLoader)`
2. **Setter methods**: `setXxx(value)` → `xxx(value)` (fluent methods)
3. **Finalization**: Must call `.load()` to create Flyway instance
4. **ClassLoader handling**: `setClassLoader()` becomes constructor parameter, not fluent method

## Transformation Rules

### Structural Transformations
1. **Constructor Replacement**:
   - Match: `new Flyway()` or `new org.flywaydb.core.Flyway()`
   - Replace: `Flyway.configure()` or `Flyway.configure(classLoaderExpr)`

2. **Setter Transformation**:
   - Match: `flyway.setDataSource(dataSource)`
   - Transform: `.dataSource(dataSource)` (as part of fluent chain)
   - Special case: `setClassLoader()` → becomes constructor parameter

3. **Method Name Mapping**:
   - `setDataSource` → `dataSource`
   - `setLocations` → `locations` (note: accepts varargs)
   - `setValidateOnMigrate` → `validateOnMigrate` (Boolean→boolean)
   - `setClassLoader` → constructor parameter (not fluent method)

4. **Chain Construction**:
   - Collect all setter calls in order
   - Build fluent chain: `Flyway.configure().xxx(arg).yyy(arg).load()`
   - Remove intermediate variable declaration

5. **Return Statement Update**:
   - Match: `return flyway;`
   - Replace with complete fluent chain

## Generic JavaParser Implementation

The transformation rule in `Main.java` demonstrates:
- Pattern matching for Flyway constructor calls
- Removal of setter method calls  
- Structural transformation without hardcoded project identifiers
- Parameterization by fully-qualified type names

## Usage
```bash
java -cp "target/classes:deps/*" github.chains.Main /path/to/project/src
```

## Limitations & Extensions
Current implementation shows the pattern matching logic. A complete solution would need:
- AST tracking of variable declarations and their types
- Mapping of setter arguments to fluent method arguments
- Handling of multiple Flyway instances in same method
- Support for method chaining (`new Flyway().setXxx().setYyy()`)
- Configuration method ordering and validation