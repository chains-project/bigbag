# SLF4J LoggingEventAware Fixer

This is a generic Spoon transformation that fixes compilation errors caused by the removal of `org.slf4j.spi.LoggingEventAware` in newer versions of SLF4J.

## Problem

In newer versions of SLF4J, the `org.slf4j.spi.LoggingEventAware` interface was removed. Projects that reference this interface in their code will fail to compile with newer versions of Logback and SLF4J.

## Solution

This transformation:
1. Removes imports of `org.slf4j.spi.LoggingEventAware`
2. Removes the interface from any class `implements` clauses
3. Removes references to the interface in type casts or method signatures

## Usage

1. Compile the project:
   ```
   mvn clean package
   ```

2. Run the transformation:
   ```
   java -jar target/spoon-base-1.0-SNAPSHOT.jar <path-to-source-code>
   ```

The transformed code will be written to the `transformed-src` directory.

## How it works

The transformation is generic and can be applied to any Maven project with this issue. It searches for:
- Import statements referencing `org.slf4j.spi.LoggingEventAware`
- Classes implementing `org.slf4j.spi.LoggingEventAware`
- Any direct references to the interface in method signatures or type casts

It then safely removes these references to make the code compatible with newer SLF4J versions.