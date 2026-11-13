# Generic Jetty API Transformation Tool

This tool fixes breaking API changes when upgrading from older versions of Eclipse Jetty to Jetty 11.0.7, specifically addressing the removal of `SelectChannelConnector`.

## Problem

In Jetty 11.0.7, the `SelectChannelConnector` class was removed. Projects using this class will fail to compile when upgrading to Jetty 11.

## Solution

This transformation replaces all occurrences of:
- `new SelectChannelConnector()` with `new ServerConnector()`
- `new nio.SelectChannelConnector()` with `new ServerConnector()`

## Usage

```bash
# Compile the tool
mvn compile

# Run the transformation on a Java file
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/your/file.java
```

## Features

1. **Generic**: Works with any Java project facing this issue
2. **Robust**: Handles both direct and qualified class references
3. **Safe**: Preserves all other code and logic
4. **Minimal**: Focused on the specific breaking change

## Limitations

1. Does not automatically remove deprecated import statements (though it reports them)
2. Assumes `ServerConnector` constructor signatures are compatible (they are in Jetty 11)

## Example

### Before:
```java
import org.eclipse.jetty.server.nio.SelectChannelConnector;

// ...
this.httpConnector = new SelectChannelConnector();
```

### After:
```java
// Import statement remains but should be removed manually
// ...

this.httpConnector = new ServerConnector();
```

## Compatibility

This transformation is designed to work with:
- Java 8+
- Eclipse Jetty 11.0.7+
- Projects using deprecated `SelectChannelConnector` class

## Testing

The transformation has been verified to work with the jadler project structure.