# Javax Validation to Jakarta Validation Migration Tool

This is a generic Spoon-based transformation tool designed to automatically migrate code from javax.validation to jakarta.validation, addressing common breaking changes when migrating from Java EE to Jakarta EE.

## Problem Addressed

When migrating from Java EE to Jakarta EE, many projects encounter compilation errors like:
```
package javax.validation does not exist
```

This occurs because javax.validation was renamed to jakarta.validation in the Jakarta EE transition.

## Solution

This tool automatically:
1. Converts import statements from `javax.validation` to `jakarta.validation`
2. Updates all references to validation annotations
3. Handles all sub-packages of validation

## Usage

```bash
java -cp target/classes github.chains.JavaxValidationToJakartaTransformation /path/to/source/code
```

## How It Works

The transformation uses Spoon's AST processing capabilities to:
1. Parse Java source files
2. Identify validation API usages
3. Automatically update package references
4. Preserve original code functionality

## Generic Design

This transformation is designed to be reusable across projects facing the same breaking change:
- Works with any Maven project using javax.validation
- Handles all standard validation annotations (@Valid, @NotNull, @NotBlank, etc.)
- Can be extended for other API migrations
- Maintains backward compatibility

## Example

Before:
```java
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
```

After:
```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
```

## Requirements

- Java 17+
- Maven 3.6+
- Spoon library (version 11.2.1)

## Limitations

Currently handles basic import transformations. For complex cases involving pattern matching, more sophisticated spoon patterns would be required.