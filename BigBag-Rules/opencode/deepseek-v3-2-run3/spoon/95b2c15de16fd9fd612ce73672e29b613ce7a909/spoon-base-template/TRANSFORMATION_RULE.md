# Generic Jetty 11 Migration Transformation Rule

## Overview
This is a GENERIC, REUSABLE Spoon transformation rule for migrating projects from Jetty 8/9 to Jetty 11. It addresses the breaking API changes introduced in Jetty 11.

## Breaking Changes Addressed

### 1. Package Migration: javax.servlet → jakarta.servlet
**Structural Pattern:** `javax.servlet.*` → `jakarta.servlet.*`
- Applies to ALL references (imports, type declarations, method parameters, etc.)
- Generic pattern: match any type starting with `javax.servlet`

### 2. Connector Class Replacement: SelectChannelConnector → ServerConnector  
**Structural Pattern:** `new SelectChannelConnector()` → `new ServerConnector(server)`
- Note: ServerConnector constructor requires Server as first parameter
- Manual adjustment needed for constructor arguments

### 3. Removed Server Methods
**Structural Patterns:**
- `server.setSendServerVersion(false)` → `// removed in Jetty 11`
- `server.setSendDateHeader(true)` → `// removed in Jetty 11`
- These methods were completely removed from the Server API

### 4. Connector API Changes
**Structural Patterns:**
- `connector.setPort(8080)` → `serverConnector.setPort(8080)` (if target is ServerConnector)
- `connector.getLocalPort()` → `serverConnector.getLocalPort()` (if target is ServerConnector)
- Methods moved from Connector interface to ServerConnector class
- Requires type checking at call sites

### 5. AbstractHandler.handle() Signature Change
**Structural Pattern:**
- `handle(String, Request, HttpServletRequest, HttpServletResponse)`
  → `handle(String, Request, jakarta.servlet.HttpServletRequest, jakarta.servlet.HttpServletResponse)`
- Parameter types changed from javax.servlet to jakarta.servlet

## Transformation Configuration

The transformation is parameterized by these constants (fully-qualified type names from Jetty 11):

```java
// Old API patterns (to match)
private static final String JAVAX_SERVLET_PREFIX = "javax.servlet";
private static final String SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
private static final String CONNECTOR_INTERFACE = "org.eclipse.jetty.server.Connector";
private static final String SERVER_CLASS = "org.eclipse.jetty.server.Server";
private static final String ABSTRACT_HANDLER = "org.eclipse.jetty.server.handler.AbstractHandler";

// New API patterns (to replace with)
private static final String JAKARTA_SERVLET_PREFIX = "jakarta.servlet";
private static final String SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";

// Methods to handle
private static final Set<String> REMOVED_SERVER_METHODS = Set.of(
    "setSendServerVersion", "setSendDateHeader"
);
private static final Set<String> CONNECTOR_METHODS_TO_CHECK = Set.of(
    "setPort", "getLocalPort"
);
```

## Usage

```bash
java github.chains.Main <source-directory> [output-report-file]
```

Example:
```bash
# Apply to jadler project
java github.chains.Main /workspace/jadler/jadler-jetty migration-report.txt
```

## Output
1. **Transformed source code** in `spooned/` directory
2. **Migration report** with:
   - Applied fixes count
   - Warnings/notes count  
   - Manual review requirements
   - Specific locations of changes

## Manual Review Required
The transformation identifies but cannot automatically fix:
1. **Constructor argument adjustment** for ServerConnector
2. **Type verification** for Connector method calls
3. **Configuration updates** for removed Server methods

## Generic Applicability
This transformation works for ANY project affected by Jetty 11 breaking changes because:
1. Uses structural patterns based on Jetty API types
2. Parameterized by Jetty type names (not project-specific)
3. Traverses entire AST applying fixes wherever patterns match
4. Generates comprehensive report for manual follow-up

## Implementation Notes
The transformation uses Spoon's AST manipulation API to:
1. Traverse all packages, types, methods, and statements
2. Match structural patterns in the code
3. Apply type-safe transformations
4. Generate detailed migration reports