# Generic JavaParser Transformation Rule for Dependency Breaking Changes

This document describes a generic JavaParser transformation rule designed to fix breaking changes introduced by dependency updates, particularly focusing on the removal of `SelectChannelConnector` in Jetty 11.0.9.

## Problem Statement
In Jetty 11.0.9, the `SelectChannelConnector` class was removed from the API. Projects using older versions of Jetty (like Jadler) that reference this class would encounter compilation errors when upgrading to newer versions.

## Solution Overview

The transformation rule provides a mechanism to automatically fix these breaking changes by:

1. Identifying all usages of `SelectChannelConnector` in object creation expressions
2. Replacing them with the appropriate alternative API (`ServerConnector`)
3. Removing outdated import statements

## Implementation Details

### Core Classes and Methods

#### `Main`

The main transformation utility with these key methods:

1. `transformFile(Path filePath)` - Main entry point that processes a Java file
2. `transformSelectChannelConnector(CompilationUnit cu)` - Replaces `SelectChannelConnector` with `ServerConnector`
3. `removeSetSendServerVersion(CompilationUnit cu)` - Removes obsolete method calls
4. `removeSetSendDateHeader(CompilationUnit cu)` - Removes obsolete method calls

## Example Usage

```bash
# Compile the transformation tool
mvn compile

# Run transformation on a project
java -cp target/classes github.chains.Main /path/to/target/project
```

## Specific Fix Applied

For the Jetty 11.0.9 breaking change:
```java
// Before (in Jetty 9.x):
this.httpConnector = new SelectChannelConnector();
this.server.setSendServerVersion(false);
this.server.setSendDateHeader(true);

// After (in Jetty 11.0.9+):
this.httpConnector = new ServerConnector(this.server);
// setSendServerVersion and setSendDateHeader calls removed
```

## Generic Extensibility

While focused on Jetty's specific breaking change, this framework can be extended to handle other breaking changes by:

1. Adding new replacement rules in the transformFile method
2. Supporting additional pattern matching logic
3. Implementing more sophisticated AST traversals

## Benefits

1. **Automated Fixing**: Eliminates manual code changes for common breaking changes
2. **Reusable**: Can be adapted for other dependency breaking changes
3. **Safe**: Only modifies specific, targeted patterns
4. **Maintainable**: Centralized transformation logic for easy updates

## Limitations

1. Requires JavaParser library for AST manipulation
2. May need updates for new breaking change patterns
3. Some complex replacements might require more sophisticated logic