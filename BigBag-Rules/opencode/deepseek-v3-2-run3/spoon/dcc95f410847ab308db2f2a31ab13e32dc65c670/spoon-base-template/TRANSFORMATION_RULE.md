# Generic Transformation Rule for logback-classic 1.4.5 Breaking Change

## Problem Description
When upgrading to logback-classic 1.4.5, projects using SLF4J 1.x encounter compilation errors because:
- `ch.qos.logback.classic.Logger` now implements `org.slf4j.spi.LoggingEventAware` interface
- This interface doesn't exist in SLF4J 1.x API
- Any code referencing `ch.qos.logback.classic.Logger` type fails to compile with error: "cannot access org.slf4j.spi.LoggingEventAware"

## Breaking Change Pattern
- **Old API pattern**: `ch.qos.logback.classic.Logger` implements SLF4J 1.x interfaces only
- **New API pattern**: `ch.qos.logback.classic.Logger` implements SLF4J 2.x interfaces including `LoggingEventAware`
- **Structural transformation required**: Avoid referencing `ch.qos.logback.classic.Logger` type directly

## Spoon Transformation Solution
The transformation rule in `Main.java`:
1. Finds all type references to `ch.qos.logback.classic.Logger`
2. Replaces them with `org.slf4j.Logger` references
3. Identifies potential logback-specific method calls that will need manual refactoring

## How to Use
1. Compile the transformation:
   ```bash
   mvn compile
   mvn package
   ```

2. Run the transformation on your project:
   ```bash
   java -jar spoon-transformation.jar <source-directory> <output-directory>
   ```

3. Manual refactoring required after transformation:
   - Logback-specific method calls (`setLevel()`, `addAppender()`, etc.) won't compile
   - Options:
     a. Use reflection to call these methods
     b. Rewrite tests to not rely on logback internals  
     c. Create a utility class with reflection helpers
     d. Upgrade SLF4J to 2.x (recommended for full compatibility)

## Example Manual Fix
For test code like:
```java
Logger logger = (Logger) LoggerFactory.getLogger(MyClass.class);
logger.setLevel(Level.INFO);
logger.addAppender(mockAppender);
```

Change to use reflection:
```java
Object logger = LoggerFactory.getLogger(MyClass.class);
// Reflection for setLevel
logger.getClass().getMethod("setLevel", Level.class).invoke(logger, Level.INFO);
// Reflection for addAppender  
logger.getClass().getMethod("addAppender", Appender.class).invoke(logger, mockAppender);
```

## Generality and Reusability
This transformation is generic because:
1. It addresses the core issue: `ch.qos.logback.classic.Logger` type cannot be referenced
2. It works for ANY project affected by this breaking change
3. The only project-specific input is the source directory path
4. No hardcoded project identifiers in the transformation

## Limitations
1. Doesn't automatically fix logback-specific method calls (requires manual work)
2. Assumes projects can adapt to using `org.slf4j.Logger` interface instead of concrete `ch.qos.logback.classic.Logger` type
3. Best used alongside SLF4J 2.x upgrade for complete solution

## Alternative Approaches
1. **Upgrade SLF4J to 2.x**: Most comprehensive solution
2. **Add shim interfaces**: Add empty `org.slf4j.spi.LoggingEventAware` interface to project
3. **Use reflection wrappers**: Create utility class that uses reflection for all logback operations

The transformation provides the foundation for approach #3 by removing type dependencies, enabling reflection-based solutions.