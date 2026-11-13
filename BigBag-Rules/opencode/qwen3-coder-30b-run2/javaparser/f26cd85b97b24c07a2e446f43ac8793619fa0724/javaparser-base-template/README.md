# Generic Dependency Update Transformation Framework

This is a generic JavaParser transformation framework designed to fix breaking changes in dependency updates. It specifically addresses the scenario where a dependency like Jetty was updated and broke existing APIs (e.g., SelectChannelConnector was removed in Jetty 11).

## Problem Addressed

When upgrading dependencies, breaking changes often occur:
- Classes/methods are deprecated/removed 
- Method signatures change
- Constructors are modified

This framework provides a reusable pattern to address such breaking changes.

## How It Works

The transformation framework:
1. Identifies problematic API patterns in Java source code
2. Replaces them with equivalent modern API usage
3. Is designed to be generic and reusable across projects

## Usage Example

```bash
# Build the transformation tool
mvn compile

# Run transformation on a project
java -cp target/classes github.chains.Main /path/to/project
```

## Implementation

The framework specifically targets Jetty 11 breaking changes:
1. Replaces `new SelectChannelConnector()` with `new ServerConnector(server)`
2. Removes deprecated method calls like `server.setSendServerVersion()`
3. Removes deprecated method calls like `server.setSendDateHeader()`

## Generic Pattern

The framework follows a pattern that can be extended for other breaking changes:
- Structural pattern matching using JavaParser
- Reusable across projects 
- Parameterized by dependency API specifications