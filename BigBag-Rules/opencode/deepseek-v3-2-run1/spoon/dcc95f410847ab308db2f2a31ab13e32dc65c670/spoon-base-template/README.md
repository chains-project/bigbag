# Generic Spoon Transformation for Logback 1.4.5 + SLF4J < 2.0.0 Compatibility

## Problem
When upgrading from logback-classic < 1.4.5 to >= 1.4.5 while using SLF4J < 2.0.0, compilation fails because:
- `ch.qos.logback.classic.Logger` in version 1.4.5 implements `org.slf4j.spi.LoggingEventAware`
- `org.slf4j.spi.LoggingEventAware` doesn't exist in SLF4J versions prior to 2.0.0
- Code that uses `ch.qos.logback.classic.Logger` directly fails to compile

## Solution
This generic Spoon transformation automatically fixes code patterns that cause compilation errors.

## Patterns Detected and Fixed

### 1. Variable declarations of type `ch.qos.logback.classic.Logger`
```java
// Before:
ch.qos.logback.classic.Logger logger = ...;

// After:
org.slf4j.Logger logger = ...;
```

### 2. Method calls on `ch.qos.logback.classic.Logger` instances
- `setLevel()`, `addAppender()`, `detachAppender()`, `detachAndStopAllAppenders()`
- These are logback-specific methods not available on `org.slf4j.Logger`
- Transformation adds TODO comments suggesting alternatives

### 3. Casts to `ch.qos.logback.classic.Logger`
```java
// Before:
Logger logger = (Logger) LoggerFactory.getLogger(...);

// After (if possible):
Logger logger = LoggerFactory.getLogger(...);
```

## Building and Running

### Build:
```bash
cd /workspace/spoon-base-template
mvn clean package
```

### Run:
```bash
java -jar target/spoon-base-1.0-SNAPSHOT.jar <source-directory>
```

Example:
```bash
java -jar target/spoon-base-1.0-SNAPSHOT.jar /path/to/project/src
```

### Output:
Transformed code is saved to `<source-directory>-transformed`

## Generic and Reusable
This transformation is NOT a one-off patch. It:
- Uses pattern matching to find all occurrences of the problematic patterns
- Works on ANY Java project affected by this breaking change
- Requires only the source directory path as input
- Contains no hardcoded project-specific identifiers
- Can be applied to other projects by simply changing the input source directory

## Limitations
- Some logback-specific methods (like `setLevel()`, `addAppender()`) cannot be automatically replaced
  - These methods are added with TODO comments
  - Manual intervention may be needed for tests that require logback-specific functionality
- Complex cast patterns may require additional manual fixes

## Recommendations
1. Consider upgrading SLF4J to version 2.0.0 or later for full compatibility with logback-classic 1.4.5+
2. Or downgrade logback-classic to version 1.3.x or 1.2.x
3. Review TODO comments added by the transformation for logback-specific method calls