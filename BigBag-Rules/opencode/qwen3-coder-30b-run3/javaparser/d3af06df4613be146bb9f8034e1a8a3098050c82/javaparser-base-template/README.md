# Generic Jetty 11.0.8 API Transformation Rule

This project demonstrates a generic transformation rule for upgrading projects from older Jetty versions to Jetty 11.0.8.

## Breaking Changes Addressed

The following API changes in Jetty 11.0.8 require code modifications:

1. **Deprecated Methods Removed**:
   - `Server.setSendServerVersion(boolean)` - removed
   - `Server.setSendDateHeader(boolean)` - removed

2. **Connector Class Changes**:
   - `org.eclipse.jetty.server.nio.SelectChannelConnector` - removed
   - Use `org.eclipse.jetty.server.ServerConnector` instead

3. **Package Restructuring**:
   - `org.eclipse.jetty.server.nio` package removed
   - Imports referencing this package need to be removed

## Generic Transformation Patterns

This transformation rule matches and fixes the following patterns:

### Pattern 1: Deprecated API Calls
**Before**:
```java
server.setSendServerVersion(false);
server.setSendDateHeader(true);
```

**After**:
Remove these calls entirely (they are no longer supported)

### Pattern 2: Connector Instantiation
**Before**:
```java
import org.eclipse.jetty.server.nio.SelectChannelConnector;
...
connector = new SelectChannelConnector();
```

**After**:
```java
import org.eclipse.jetty.server.ServerConnector;
...
connector = new ServerConnector(server);
```

### Pattern 3: Deprecated Imports
**Before**:
```java
import org.eclipse.jetty.server.nio.SelectChannelConnector;
```

**After**:
Remove the import statement

## Usage

```bash
mvn compile
java -cp target/classes github.chains.Main /path/to/project
```

## Target Audience

This transformation is intended for developers upgrading projects that use Jetty 8/9 to Jetty 11.0.8, particularly for projects that use the `org.eclipse.jetty.server` package directly.