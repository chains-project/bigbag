# Flyway Breaking Change Transformation

## Problem

The nem project's Flyway dependency was updated from an older version to 9.10.2. This introduced a breaking change where:
- The `Flyway()` constructor now requires a `Configuration` parameter
- Setter methods like `setDataSource()`, `setLocations()`, etc. have been removed
- The new API uses a fluent `FluentConfiguration` pattern instead

## Old API Pattern (Before)
```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setLocations("db/migration");
flyway.setValidateOnMigrate(true);
```

## New API Pattern (After)
```java
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .locations("db/migration")
    .validateOnMigrate(true)
    .load();
```

## Solution

This transformation identifies all instances of the old Flyway instantiation pattern and converts them to the new fluent API pattern. The transformation:

1. Detects `new Flyway()` instantiations without arguments
2. Converts them to `Flyway.configure().load()` pattern
3. Transforms setter method calls to fluent method calls
4. Maintains all the same configuration parameters

## Files Modified

The transformation will fix:
- `/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java`
- `/workspace/nem/nis/src/test/java/org/nem/nis/dao/TestConf.java`

## How to Use

1. Compile the transformation tool:
   ```bash
   mvn compile
   ```

2. Run the transformation on your project:
   ```bash
   java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) github.chains.Main /path/to/your/project
   ```

## Manual Fix Example

For the nem project, you would change:

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

To:

```java
@Bean(initMethod = "migrate")
public Flyway flyway() throws IOException {
    final Properties prop = new Properties();
    prop.load(NisAppConfig.class.getClassLoader().getResourceAsStream("db.properties"));

    final org.flywaydb.core.Flyway flyway = Flyway.configure()
        .dataSource(this.dataSource())
        .classLoader(NisAppConfig.class.getClassLoader())
        .locations(prop.getProperty("flyway.locations"))
        .validateOnMigrate(Boolean.valueOf(prop.getProperty("flyway.validate")))
        .load();
    return flyway;
}
```

## Testing

The transformation has been tested and successfully identified the Flyway instantiation patterns in the nem project. Running the tool will show which files were modified.