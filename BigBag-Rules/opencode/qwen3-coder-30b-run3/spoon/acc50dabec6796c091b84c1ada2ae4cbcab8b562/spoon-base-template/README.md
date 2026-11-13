# Generic xwork2 API Breaking Changes Fixer

This is a generic Spoon transformation that can be applied to fix breaking API changes in xwork2/struts2-core dependencies.

## Problem Description

The @guice/ project fails to compile due to breaking dependency updates in struts2-core. This transformation addresses common breaking changes in the xwork2 API that would affect any project using struts2-core.

## Key Features

- Identifies potentially problematic xwork2 constructor calls
- Detects method signature changes in xwork2 classes
- Recognizes ObjectFactory implementations that might need updates
- Provides a reusable framework for fixing breaking changes

## Usage

```java
// Create a Spoon launcher
Launcher launcher = new Launcher();
launcher.addInputResource("path/to/source");

// Process the model
CtModel model = launcher.buildModel();

// Apply the transformation
Main.fixBreakingApiChanges(model);
```

## Common Breaking Changes Addressed

1. **Constructor Signature Changes**: Modifications to entity class constructors (ActionConfig, InterceptorConfig, etc.)
2. **Method Signature Changes**: Updates to buildInterceptor, buildBean, createActionProxy methods
3. **Builder Pattern Changes**: Modifications to configuration entity builders

## Generic Transformation Pattern

The transformation follows a pattern that:
- Uses structural matching to identify patterns rather than specific class names
- Applies fixes based on API change patterns rather than specific breaking changes
- Is reusable across multiple projects with similar dependency issues