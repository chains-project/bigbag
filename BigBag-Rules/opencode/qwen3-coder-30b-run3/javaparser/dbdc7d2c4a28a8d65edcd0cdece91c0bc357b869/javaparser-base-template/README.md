# Generic JavaParser Transformation for plexus-archiver enableLogging Removal

This tool solves the breaking change in plexus-archiver 4.4.0 where the `enableLogging` method was removed from `AbstractUnArchiver`.

## Problem

In plexus-archiver 4.4.0, the `enableLogging(org.codehaus.plexus.logging.Logger)` method was removed from `AbstractUnArchiver`. This breaks existing code that calls:
```java
unArchiver.enableLogging(new ConsoleLogger(Logger.LEVEL_DEBUG, "Sauce"));
```

## Solution

This JavaParser-based transformation automatically removes all calls to `enableLogging` method throughout a Java codebase.

## Usage

```bash
# Compile the transformation tool
mvn compile

# Run the transformation on your Java project
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/your/java/sources
```

## How It Works

1. Recursively scans all `.java` files in the specified directory
2. Parses each file using JavaParser
3. Finds all method calls to `enableLogging` 
4. Removes the entire statement containing the method call
5. Saves the modified file back to disk

## Limitations

- This transformation removes the logging configuration calls entirely
- If logging is still required, developers will need to implement alternative logging mechanisms compatible with plexus-archiver 4.4.0+
- The transformation preserves all other code structure and formatting

## Compatibility

This tool is designed to be generic and reusable across any Maven project affected by the same breaking change.