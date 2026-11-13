# Generic Jetty 11 Compatibility Transformer

This is a generic transformation rule to fix breaking changes in Jetty 11 compatibility issues.

## Problem
The @jadler/ project fails to compile with Jetty 11 due to breaking changes:
1. `SelectChannelConnector` has been replaced with `ServerConnector`
2. The `org.eclipse.jetty.server.nio` package no longer exists
3. Method signature changes in AbstractHandler implementations

## Solution
This transformation replaces:
- All instances of `SelectChannelConnector` with `ServerConnector`
- Removes imports for `org.eclipse.jetty.server.nio` package

## Usage
```bash
# Run on jadler project directory
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout) github.chains.Main /workspace/jadler
```