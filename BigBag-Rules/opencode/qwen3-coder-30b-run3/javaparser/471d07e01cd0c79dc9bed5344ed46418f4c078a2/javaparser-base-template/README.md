# Generic CloudResourceManager API Transformation Template

This is a generic JavaParser-based transformation template designed to handle breaking changes in Google Cloud Resource Manager API dependencies.

## Problem Context

When a Google Cloud Resource Manager dependency updates (such as from v3-rev20220807-1.0.0 to v3-rev20220807-2.0.0), it often introduces breaking changes that require code modifications. This template provides a reusable framework to address such breaking changes.

## Key Features

- Generic pattern matching for CloudResourceManager API usage
- Extensible architecture for specific breaking change implementations
- Structured approach to identify and fix common breaking patterns
- JavaParser integration for safe AST-based code transformation

## Breaking Change Patterns Addressed

1. **Constructor signature changes** in `CloudResourceManager.Builder`
2. **Method signature changes** in request objects (`setXXX` methods)
3. **Removal or deprecation** of fluent API patterns
4. **Parameter addition** to existing methods

## Usage

### Compile the template:
```bash
mvn compile
```

### Run the template:
```bash
java -cp target/classes github.chains.Main
```

### For a specific project:
```bash
java -cp target/classes github.chains.Main /path/to/your/project
```

## Implementation Structure

The template provides:

1. **Main.java** - Entry point with documentation
2. **CloudResourceManagerTransformer class** - Base visitor for AST traversal 
3. **Extensible framework** - Ready for specific breaking change implementations

## How to Adapt for Specific Breaking Changes

1. Create a subclass of `CloudResourceManagerTransformer` 
2. Override relevant `visit()` methods to detect the specific breaking change pattern
3. Implement fix logic within the visitor methods
4. Apply transformation using JavaParser's modifier visitor pattern

## Example Transformation Logic

For a breaking change where a method signature changed from:
```java
builder.setSomeParameter(String param)
```
to:
```java
builder.setSomeParameter(String param, boolean flag)
```

The transformation would:
1. Find all `setSomeParameter()` calls
2. Add the missing boolean parameter where appropriate
3. Ensure compatibility with the new API

## Reusability

This template is designed to be reusable across different projects and breaking change scenarios by:
- Using generic patterns instead of project-specific identifiers
- Providing clear extension points
- Following standard JavaParser visitor patterns
- Separating detection from transformation logic