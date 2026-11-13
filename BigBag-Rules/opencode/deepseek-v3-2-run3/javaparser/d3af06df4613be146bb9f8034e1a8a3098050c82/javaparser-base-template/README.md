# Jetty 8/9 to Jetty 11 Migration Transformation

## Overview
This JavaParser-based transformation automates the migration from Jetty 8/9 to Jetty 11 APIs. It handles breaking changes introduced in Jetty 11, particularly the migration from `javax.servlet` to `jakarta.servlet` and the removal/replacement of deprecated classes.

## Breaking Changes Addressed

### 1. Package Migration: javax.servlet -> jakarta.servlet
- **Old**: `javax.servlet.*` imports
- **New**: `jakarta.servlet.*` imports
- **Transformation**: Automatic import replacement

### 2. Class Removal: SelectChannelConnector -> ServerConnector
- **Old**: `org.eclipse.jetty.server.nio.SelectChannelConnector`
- **New**: `org.eclipse.jetty.server.ServerConnector`
- **Transformation**: Automatic class replacement with basic constructor adaptation

### 3. API Relocation: Server Configuration Methods
- **Old**: `Server.setSendServerVersion(boolean)`, `Server.setSendDateHeader(boolean)`
- **New**: Configure via `HttpConfiguration` attached to `ServerConnector`
- **Guidance**: Provides migration instructions; requires manual code adjustment

### 4. Interface Change: Connector Methods
- **Old**: `Connector.setPort(int)`, `Connector.getLocalPort()`
- **New**: Methods now on `NetworkConnector` interface
- **Transformation**: Attempts to add cast expressions; provides guidance for manual fixes

## Usage

```bash
# Compile the transformation
mvn clean compile package

# Run on source directory
java -jar target/javaparser-1.0-SNAPSHOT.jar /path/to/source/code
```

## Transformation Rules

### Automatic Transformations
1. **Import Updates**: All `javax.servlet` imports are changed to `jakarta.servlet`
2. **Class Replacement**: `SelectChannelConnector` references are replaced with `ServerConnector`
3. **Import Addition**: Adds necessary Jetty 11 imports (`NetworkConnector`, `HttpConfiguration`, `HttpConnectionFactory`)

### Guided Transformations
1. **Server Configuration**: Detects `setSendServerVersion`/`setSendDateHeader` calls and provides migration guidance
2. **Port Configuration**: Attempts to add `NetworkConnector` casts for `setPort`/`getLocalPort` methods

## Example Migration

### Before Transformation
```java
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

public class JettyStubHttpServer {
    public JettyStubHttpServer(int port) {
        Server server = new Server();
        server.setSendServerVersion(false);
        server.setSendDateHeader(true);
        
        Connector connector = new SelectChannelConnector();
        connector.setPort(port);
        server.addConnector(connector);
    }
    
    public int getPort() {
        return httpConnector.getLocalPort();
    }
}
```

### After Transformation
```java
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;

public class JettyStubHttpServer {
    public JettyStubHttpServer(int port) {
        Server server = new Server();
        server.setSendServerVersion(false);  // NOTE: Needs manual migration
        server.setSendDateHeader(true);       // NOTE: Needs manual migration
        
        Connector connector = new ServerConnector(server);
        connector.setPort(port);  // NOTE: May need ((NetworkConnector) connector).setPort(port)
        server.addConnector(connector);
    }
    
    public int getPort() {
        return ((NetworkConnector) httpConnector).getLocalPort();
    }
}
```

### Manual Completion Required
```java
public JettyStubHttpServer(int port) {
    Server server = new Server();
    
    // Create HttpConfiguration for Jetty 11
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSendServerVersion(false);
    httpConfig.setSendDateHeader(true);
    
    // Create ServerConnector with HttpConnectionFactory
    ServerConnector connector = new ServerConnector(server, 
        new HttpConnectionFactory(httpConfig));
    connector.setPort(port);  // setPort is available on ServerConnector
    
    server.addConnector(connector);
    this.httpConnector = connector;
}
```

## Design Principles

1. **Safety First**: Prefers adding guidance over making risky automatic changes
2. **Generality**: Transformation rules work on any Java codebase using Jetty 8/9 APIs
3. **Reusability**: No project-specific hardcoding; uses pattern matching
4. **Clarity**: Provides detailed guidance for manual completion steps

## Limitations

1. **Complex Refactoring**: Cannot automatically refactor complex constructor logic
2. **Type Inference**: Limited ability to infer variable types for proper casting
3. **Configuration Patterns**: Cannot automatically create `HttpConfiguration` setup
4. **Multi-file Analysis**: Limited cross-file analysis capability

## Testing

The transformation has been tested on the `@jadler/jadler-jetty` project and successfully:
- Updated all `javax.servlet` imports to `jakarta.servlet`
- Replaced `SelectChannelConnector` with `ServerConnector`
- Added necessary Jetty 11 imports
- Provided guidance for manual completion

## Extension Points

The transformation can be extended to handle additional Jetty API changes by:
1. Adding new import replacement rules in `visit(ImportDeclaration)`
2. Adding new class replacement rules in `visit(ObjectCreationExpr)`
3. Adding new method transformation rules in `visit(MethodCallExpr)`
4. Enhancing the guidance output for new migration patterns