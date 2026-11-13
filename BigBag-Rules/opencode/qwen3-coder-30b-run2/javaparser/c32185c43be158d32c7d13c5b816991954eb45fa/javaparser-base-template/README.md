# Generic Flyway API Fix Transformation

This is a transformation rule to fix breaking changes in Flyway API from version 6.x to 9.x.

## Problem
In Flyway 9.x, the API changed from:
```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource());
flyway.setLocations("db/migration");
```

To:
```java
Flyway flyway = Flyway.configure()
    .dataSource(dataSource())
    .locations("db/migration")
    .load();
```

## Solution
This transformation identifies the old instantiation pattern and replaces it with the new FluentConfiguration API pattern.

The transformation works by:
1. Finding methods that create Flyway instances using `new Flyway()` 
2. Identifying subsequent setter calls on that instance
3. Replacing the entire pattern with the new FluentConfiguration approach

## Usage
```
mvn compile
java -cp target/classes github.chains.Main /path/to/source/code
```