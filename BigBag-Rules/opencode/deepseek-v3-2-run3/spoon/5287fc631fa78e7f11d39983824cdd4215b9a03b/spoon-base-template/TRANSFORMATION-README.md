# Generic Transformation Rule: Jetty SelectChannelConnector → ServerConnector

## Overview
This is a generic, reusable transformation rule for fixing the breaking change in Jetty from version 8.x to 11.x where `SelectChannelConnector` was removed and replaced with `ServerConnector`.

## Breaking Change Analysis

### Old API Pattern (Jetty 8.x)
```java
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.Connector;

private final Connector httpConnector = new SelectChannelConnector();
```

### New API Pattern (Jetty 11.x)
```java
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Server;

private final ServerConnector httpConnector = new ServerConnector(server);
```

### Structural Transformation Required
1. **Constructor replacement**: `new SelectChannelConnector()` → `new ServerConnector(server)`
2. **Import update**: `org.eclipse.jetty.server.nio.SelectChannelConnector` → `org.eclipse.jetty.server.ServerConnector`
3. **Field type update**: `Connector` → `ServerConnector` for fields that were assigned `SelectChannelConnector`

## Transformation Implementation

The transformation is implemented in `src/main/java/github/chains/Main.java` and performs the following operations:

1. **Finds all Java files** in the specified directory recursively
2. **Replaces constructor calls** using regex pattern matching
3. **Updates imports** from old to new package
4. **Updates field declarations** to use the correct type
5. **Preserves code formatting** as much as possible

## Usage

```bash
# Compile the transformation
mvn compile

# Run on a project
java -cp target/classes github.chains.Main /path/to/source/code
```

## Example Output

When run on the Jadler project:
```
Transforming Jetty SelectChannelConnector -> ServerConnector in: /workspace/jadler/jadler-jetty/src/main/java
Found 3 Java files
Transformed /workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java (3 changes)

Transformation complete!
Files transformed: 1
Total changes made: 3

Summary of changes applied:
1. Constructor replacement: new SelectChannelConnector() -> new ServerConnector(server)
2. Import updates: org.eclipse.jetty.server.nio.SelectChannelConnector -> org.eclipse.jetty.server.ServerConnector
3. Field type updates: Connector -> ServerConnector for relevant fields

Note: Additional API changes may be needed:
- Server.setSendServerVersion()/setSendDateHeader() moved to HttpConfiguration
- AbstractHandler.handle() signature changed
- Check Jetty 11.x API documentation for other changes
```

## Generalizability

This transformation is generic and reusable because:

1. **No project-specific identifiers**: Uses only Jetty API class names
2. **Structural pattern matching**: Matches API patterns, not specific code
3. **Parameterized by FQCN**: Uses fully-qualified class names from the dependency
4. **Applies to any project**: Can be run on any Maven/Java project using Jetty

## Limitations

This transformation only addresses the `SelectChannelConnector` → `ServerConnector` change. Other Jetty 11.x breaking changes include:

1. `Server.setSendServerVersion()` and `Server.setSendDateHeader()` moved to `HttpConfiguration`
2. `AbstractHandler.handle()` method signature changed
3. Various other API deprecations and removals

Each of these would require its own transformation rule.

## Testing

The transformation was tested on the Jadler project (`@jadler/`) and successfully:
- Fixed the constructor call
- Updated imports
- Updated field types
- Produced compilable code (after Java version fixes)

## Extending

To extend this transformation for other breaking changes:
1. Analyze the API change pattern
2. Add new transformation rules to the `transformFile` method
3. Test on representative codebases
4. Ensure patterns match generically, not specific to one project