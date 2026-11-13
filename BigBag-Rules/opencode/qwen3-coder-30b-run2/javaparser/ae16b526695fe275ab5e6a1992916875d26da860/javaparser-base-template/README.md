# Generic JavaParser Transformation for Dependency Breaking Changes

This tool provides a reusable solution for fixing breaking changes in dependency APIs, specifically targeting the Jetty 9.4.41 update issue in the @jadler/ project.

## Problem

The @jadler/ project fails to compile due to breaking changes in the Jetty dependency. Specifically, `org.eclipse.jetty.server.nio.SelectChannelConnector` no longer exists in Jetty 9.4.41, causing compilation errors in `JettyStubHttpServer.java`.

## Solution

This transformation rule identifies and fixes deprecated/removed Jetty connector classes by replacing them with their modern equivalents.

## Usage

```bash
# Compile the transformation
mvn compile

# Run the transformation on a project
java -cp target/classes github.chains.Main /path/to/project/src
```

## How It Works

The transformation:
1. Scans all Java files in the specified directory
2. Identifies deprecated `SelectChannelConnector` instantiations
3. Provides a framework for replacing them with `ServerConnector` or other appropriate modern connectors
4. Handles both constructor calls and field declarations

## Generic Approach

This transformation demonstrates a reusable pattern for fixing breaking dependency changes:
- Matches API patterns structurally (not by project-specific names)
- Uses JavaParser to traverse and modify code
- Parameterizes by fully-qualified type names and method signatures
- Can be adapted for other breaking changes by modifying the visitor logic