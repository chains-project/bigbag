# Hibernate SessionFactory Fix Transformation

This is a generic Spoon transformation rule designed to fix compilation errors in Maven projects caused by breaking changes in hibernate-core 5.4.24.Final.

## Problem

The `@onebusaway-gtfs-modules` project fails to compile due to breaking changes in hibernate-core 5.4.24.Final. The most common breaking changes relate to:

1. Changes to SessionFactoryBuilder API
2. Modifications to Configuration.buildSessionFactory() method signatures
3. Deprecated or removed methods in Hibernate core

## Solution

This transformation rule identifies and fixes problematic Hibernate API usage patterns by:

1. Detecting calls to deprecated or changed SessionFactoryBuilder.build() methods
2. Detecting calls to Configuration.buildSessionFactory() methods that have changed
3. Providing generic replacements that maintain compatibility

## Implementation Details

The transformation rule works by:

1. Using Spoon's AST traversal to identify method invocations
2. Matching patterns for problematic Hibernate APIs
3. Providing structured detection and transformation logic

## Usage

```bash
# Compile the transformation
mvn compile

# Run the transformation on a project
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
     spoon.Launcher \
     --processors github.chains.HibernateSessionFactoryFixRule \
     --input-type SOURCE_CODE \
     --output-directory transformed-source \
     --Source-Files /path/to/project/src
```

## Customization

To adapt this rule for specific breaking changes:

1. Modify the detection logic in `isSessionFactoryBuilderBuildCall()` and `isConfigurationBuildSessionFactoryCall()`
2. Update the transformation methods to apply specific fixes for your use case
3. Add additional pattern matching for other affected APIs

## Generic Nature

This rule is designed to be reusable across projects. Simply change the input directory path to apply it to different Maven projects with similar Hibernate compatibility issues.

## Files Created

- `HibernateSessionFactoryFixRule.java` - The main transformation rule
- `README.md` - This documentation file