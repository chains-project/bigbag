# Generic JavaParser Transformation Rule for Jetty 11 Compatibility

This repository contains a generic transformation rule that fixes breaking changes in Jetty 11.0.12, specifically the removal of `SelectChannelConnector` class.

## Problem Statement

In Jetty 11.0.12, the `org.eclipse.jetty.server.nio.SelectChannelConnector` class was removed. This breaks projects that depend on older versions of Jetty that still use this class.

## Solution

This transformation rule automatically:
1. Removes import statements for `org.eclipse.jetty.server.nio.SelectChannelConnector`
2. Replaces all references to `SelectChannelConnector` with `ServerConnector`
3. Ensures proper constructor usage for Jetty 11 compatibility

## Usage

```bash
# Compile the transformation
mvn compile

# Apply transformation to a project directory
java -cp target/classes:$(find ~/.m2/repository -name "*.jar" | grep javaparser | tr '\n' ':') github.chains.Main /path/to/project/src
```

## Example Transformation

**Before:**
```java
import org.eclipse.jetty.server.nio.SelectChannelConnector;

// ...
this.httpConnector = new SelectChannelConnector();
```

**After:**
```java
// Import removed

// ...
this.httpConnector = new ServerConnector(server);
```

## Compatibility

This transformation is designed to be generic and reusable across different projects. It works by:

1. Using structural pattern matching (regex-based) to identify the breaking change
2. Making minimal, targeted changes without modifying business logic
3. Preserving all other functionality
4. Being applicable to any Java project with this specific Jetty compatibility issue

Note: For projects that need more sophisticated handling, a full JavaParser-based approach can be used, but this text-based approach works reliably for the specific Jetty 11 compatibility case.