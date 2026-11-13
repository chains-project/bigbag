# Generic JavaParser Transformation Rule for Breaking Dependency Updates

## Problem
When migrating to newer versions of libraries, breaking changes can cause compilation errors in existing code. For example, Jetty 11.0.7 removed `SelectChannelConnector` class.

## Solution Overview
This generic transformation rule identifies and replaces deprecated/removed API calls with their modern equivalents.

## Transformation Pattern

### Before (Jetty 8-9):
```java
import org.eclipse.jetty.server.nio.SelectChannelConnector;

// ...
this.httpConnector = new SelectChannelConnector();
this.httpConnector.setPort(port);
```

### After (Jetty 11.0.7+):
```java
import org.eclipse.jetty.server.ServerConnector;

// ...
this.httpConnector = new ServerConnector(this.server);
this.httpConnector.setPort(port);
```

## Generic Implementation Approach

1. **Pattern Matching**: Find constructor calls to deprecated classes (`SelectChannelConnector`)
2. **Replacement Strategy**: 
   - Replace import statements
   - Replace constructor instantiation with new class
   - Adjust parameters if needed
3. **Parameter Handling**: Pass required context (like `this.server`) to new constructor

## Usage Example
```bash
java -cp target/javaparser-1.0-SNAPSHOT.jar github.chains.Main /path/to/project/src /path/to/output
```

## Key Features of This Generic Rule
- Works across multiple projects with the same breaking change
- Maintains same functionality and behavior
- Supports parameterized class names and method signatures
- No hardcoded project-specific identifiers
- Reusable transformation rule for similar breaking changes