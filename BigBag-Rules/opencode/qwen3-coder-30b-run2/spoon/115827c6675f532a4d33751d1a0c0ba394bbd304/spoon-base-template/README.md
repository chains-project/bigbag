# Generic Transformation for jakarta.servlet-api 6.0.0 Migration

This transformation addresses the breaking change in jakarta.servlet-api 6.0.0 where the `javax.servlet` package was renamed to `jakarta.servlet`.

## Problem Analysis

In jakarta.servlet-api 6.0.0, the following breaking changes occurred:
1. Package rename: `javax.servlet` → `jakarta.servlet`
2. Type compatibility issues: `javax.servlet.Filter` is no longer compatible with `jakarta.servlet.Filter`
3. API signature mismatches in Dropwizard integration

## Solution Approach

This generic transformation performs a regex-based search and replace of:
1. Import statements: `import javax.servlet.*` → `import jakarta.servlet.*`
2. Type references: `javax.servlet.Filter` → `jakarta.servlet.Filter`
3. Other package references

## Limitations

This transformation fixes only the package renaming aspect of the migration. Due to architectural changes in the underlying libraries:
- The `org.pac4j.jee.filter.AbstractConfigFilter` class from jee-pac4j 5.0.0 no longer extends `jakarta.servlet.Filter`
- Dropwizard's API expects `javax.servlet.Filter` but receives `jakarta.servlet.Filter` compatible types

Therefore, a complete solution requires updating the dependencies to versions that support jakarta.servlet-api 6.0.0.

## Usage

```
java -cp target/classes:<dependency-classpath> github.chains.Main <source-directory>
```

## Compatibility

This transformation can be applied to any Maven project that needs to migrate from jakarta.servlet-api 5.x to 6.0.0, but note that:
- It handles package renaming correctly
- It requires compatible library versions for full functionality
- For Dropwizard projects, newer versions of pac4j libraries are needed