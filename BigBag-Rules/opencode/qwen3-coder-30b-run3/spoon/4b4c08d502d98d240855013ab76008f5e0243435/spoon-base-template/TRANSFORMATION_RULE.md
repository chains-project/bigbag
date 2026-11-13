# Generic Flyway Breaking Change Transformation

## Problem Statement
The NEM project uses Flyway Core 9.15.1, which introduced breaking API changes compared to version 6.0.0. The specific issue occurs in `/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java` in the `flyway()` method.

## Breaking Changes
1. **Constructor Change**: `new Flyway()` → `Flyway.configure().load()`
2. **Setter Method Removal**: 
   - `setDataSource(...)` → `.dataSource(...)`
   - `setClassLoader(...)` → `.classLoader(...)`
   - `setLocations(...)` → `.locations(...)`
   - `setValidateOnMigrate(...)` → `.validateOnMigrate(...)`

## Transformation Rule (Generic)

### Target Pattern Recognition:
Find any code that follows this pattern:
```java
Flyway flyway = new Flyway();
flyway.setDataSource(...);
flyway.setClassLoader(...);
flyway.setLocations(...);
flyway.setValidateOnMigrate(...);
```

### Replacement Pattern:
Replace with:
```java
Flyway flyway = Flyway.configure()
    .dataSource(...)
    .classLoader(...)
    .locations(...)
    .validateOnMigrate(...)
    .load();
```

## Implementation Approach

### 1. Structural Matching
- Match constructor call: `new Flyway()`
- Match chained setter method calls on Flyway variables

### 2. AST Transformation
- Replace constructor with `Flyway.configure().load()`
- Convert setter method calls to fluent method calls
- Remove the original variable assignment and setter calls

### 3. Generic Reusability
This transformation should:
- Work for any project with the same Flyway API issue
- Use fully qualified type names for robust matching
- Not rely on project-specific class names
- Match the structural pattern, not exact code content

## Sample Input (Before):
```java
@Bean(initMethod = "migrate")
public Flyway flyway() throws IOException {
    final Properties prop = new Properties();
    prop.load(NisAppConfig.class.getClassLoader().getResourceAsStream("db.properties"));

    final org.flywaydb.core.Flyway flyway = new Flyway();
    flyway.setDataSource(this.dataSource());
    flyway.setClassLoader(NisAppConfig.class.getClassLoader());
    flyway.setLocations(prop.getProperty("flyway.locations"));
    flyway.setValidateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate")));
    return flyway;
}
```

## Sample Output (After):
```java
@Bean(initMethod = "migrate")
public Flyway flyway() throws IOException {
    final Properties prop = new Properties();
    prop.load(NisAppConfig.class.getClassLoader().getResourceAsStream("db.properties"));

    return Flyway.configure()
        .dataSource(this.dataSource())
        .classLoader(NisAppConfig.class.getClassLoader())
        .locations(prop.getProperty("flyway.locations"))
        .validateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate")))
        .load();
}
```

## Technical Implementation Notes

1. **Pattern Matching**: Use Spoon's visitor pattern to identify the constructor + setter sequence
2. **Fluent API Conversion**: Transform setter calls to fluent method calls  
3. **Return Statement**: Replace assignment statement with direct return of configured Flyway instance
4. **Code Structure**: Maintain the same scope and control flow as original code

## Testing & Validation

The transformation should:
1. Compile without errors in the original project
2. Pass all existing tests in the project
3. Work on any similar dependency upgrade scenario
4. Be parameterizable for different configurations

This transformation represents a reusable solution for the generic Flyway breaking change issue and would be applicable to any project facing the same API compatibility problems.