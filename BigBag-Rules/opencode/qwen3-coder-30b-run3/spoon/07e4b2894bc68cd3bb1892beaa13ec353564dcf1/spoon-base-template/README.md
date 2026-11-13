# Generic Spoon Transformation for Breaking Dependency Changes

This is a generic Spoon transformation rule designed to fix breaking changes in Maven projects when dependencies are updated. It provides a framework that can be customized for specific breaking changes.

## Purpose

The transformation addresses scenarios where:
- A method signature changes (parameters added/removed/modified)
- A method is removed and needs replacement
- A method is renamed
- A class is moved or renamed

## Usage

```bash
java -cp spoon-base-template/target/classes:$(find ~/.m2/repository -name "*.jar" | grep spoon | tr '\n' ':') github.chains.Main /path/to/source/directory
```

## How to Customize for Specific Breaking Changes

1. **Identify the breaking change pattern** in the dependency API
2. **Modify the `fixBreakingDependencyChanges` method** to detect that specific pattern
3. **Implement the transformation logic** for that pattern

### Example Pattern Implementation

For a method signature change from `oldMethod(String)` to `newMethod(String, boolean)`:

```java
// In fixBreakingDependencyChanges method, add:
if (signature.equals("oldMethod(java.lang.String)") && 
    declaringType.equals("org.jenkinsci.test.acceptance.SomeClass")) {
    // Modify the invocation to use the new signature
    // This would involve creating a new invocation with additional parameters
}
```

## Key Features

- **Generic**: Designed to handle various types of breaking changes
- **Reusable**: Can be adapted for different projects and dependency changes
- **Framework-based**: Provides infrastructure for implementing specific transformations
- **Non-project-specific**: Contains no hardcoded project identifiers

## Important Notes

- This is a template that needs customization for actual breaking changes
- The transformation only detects patterns but doesn't automatically fix all breaking changes
- Users must identify the specific breaking changes and implement the appropriate transformations
- The output is generated in a `-transformed` subdirectory