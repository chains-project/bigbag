# Generic JavaParser Transformation for Jetty Dependency Changes

This project provides a generic transformation rule that can fix breaking changes in Maven projects when upgrading Jetty dependencies from older versions (like 8.x) to newer versions (like 11.x).

## Problem Analysis

The main breaking change identified in the @jadler/ project was:
- **Old API**: `org.eclipse.jetty.server.nio.SelectChannelConnector`
- **New API**: `org.eclipse.jetty.server.ServerConnector`

In Jetty 11, the `SelectChannelConnector` class was removed/deprecated and replaced with `ServerConnector`.

## Solution

The transformation applies these changes systematically across Java files:

1. **Imports**: Changes `import org.eclipse.jetty.server.nio.SelectChannelConnector;` to `import org.eclipse.jetty.server.ServerConnector;`
2. **Constructor calls**: Changes `new SelectChannelConnector()` to `new ServerConnector()`

## Usage

```bash
# Compile the transformation
cd /workspace/javaparser-base-template
mvn compile

# Run transformation on a project
java -cp target/classes github.chains.Main /path/to/target/project
```

## Key Features of This Generic Solution

1. **Pattern-based**: Uses regex patterns to identify and replace problematic code
2. **Reusable**: Works on any Maven project with the same Jetty breaking change
3. **Safe**: Only modifies Java files, preserves existing structure
4. **Parameterizable**: Easy to adapt for other breaking changes by modifying the patterns

## Example Transformation

### Before:
```java
import org.eclipse.jetty.server.nio.SelectChannelConnector;

public class MyServer {
    public MyServer() {
        Connector connector = new SelectChannelConnector();
        // ...
    }
}
```

### After:
```java
import org.eclipse.jetty.server.ServerConnector;

public class MyServer {
    public MyServer() {
        Connector connector = new ServerConnector();
        // ...
    }
}
```

## How to Apply to Other Projects

To use this transformation on another project:

1. Compile the transformation: `mvn compile`
2. Run it against your project directory: `java -cp target/classes github.chains.Main /path/to/project`
3. Review changes and verify compilation still works