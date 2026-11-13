# Generic JavaParser Transformation for Breaking Dependency Updates

This tool provides a generic, reusable transformation rule that can be applied to ANY Maven project affected by breaking API changes when upgrading dependencies.

## Features

- **Generic**: Not tied to any specific project or dependency
- **Configurable**: Transformation rules are defined in external config files
- **Extensible**: Supports multiple types of breaking changes
- **Safe**: Operates on source code with configurable transformations

## Supported Transformation Types

1. **METHOD_RENAME**: Rename a method (e.g., `oldMethod()` → `newMethod()`)
2. **CLASS_RENAME**: Rename or move a class to different package
3. **PARAMETER_ADD**: Add new parameters to method calls with default values
4. **PARAMETER_REMOVE**: Remove parameters from method calls
5. **CONSTRUCTOR_CHANGE**: Handle constructor signature changes

## Configuration Format

```
CHANGE_TYPE|OLD_FQN|NEW_FQN|OLD_PARAMS|NEW_PARAMS|DEFAULTS
```

### Fields:
- `CHANGE_TYPE`: Type of transformation (METHOD_RENAME, PARAMETER_ADD, etc.)
- `OLD_FQN`: Fully qualified old name (e.g., `com.example.Class.method`)
- `NEW_FQN`: Fully qualified new name
- `OLD_PARAMS`: Comma-separated old parameter types
- `NEW_PARAMS`: Comma-separated new parameter types  
- `DEFAULTS`: Comma-separated default values for added parameters

### Examples:

```
# Method rename
METHOD_RENAME|com.example.Foo.oldMethod|com.example.Foo.newMethod|||

# Method with added parameter  
PARAMETER_ADD|com.example.Service.process|com.example.Service.process|String|String,boolean|true

# Class moved to different package
CLASS_RENAME|com.old.package.ClassName|com.new.package.ClassName|||
```

## Usage

```bash
# Compile the transformation
cd /workspace/javaparser-base-template
mvn compile

# Run transformation
mvn exec:java -Dexec.mainClass="github.chains.Main" \
  -Dexec.args="/path/to/source /path/to/config.txt"
```

## Example: GeoIP2 3.0.0 Migration

For projects upgrading from GeoIP2 2.x to 3.0.0, create a config file:

```
# Hypothetical GeoIP2 3.0.0 breaking changes
METHOD_RENAME|com.maxmind.geoip2.record.Traits.getAnonymousProxy|com.maxmind.geoip2.record.Traits.isAnonymous|||
PARAMETER_ADD|com.maxmind.geoip2.WebServiceClient.city|com.maxmind.geoip2.WebServiceClient.city|java.net.InetAddress|java.net.InetAddress,java.util.Locale|java.util.Locale.getDefault()
```

## Architecture

The transformation uses JavaParser to:
1. Parse all `.java` files in the source directory
2. Visit each method call expression
3. Match against configured transformation rules
4. Apply transformations (rename, add parameters, etc.)
5. Write transformed files back (when save() is uncommented)

## Design Principles

1. **Generic**: No hardcoded project-specific identifiers
2. **Reusable**: Same code works for different projects/dependencies
3. **Configurable**: Transformations defined externally
4. **Safe**: Operations are predictable and reversible
5. **Extensible**: New transformation types can be added