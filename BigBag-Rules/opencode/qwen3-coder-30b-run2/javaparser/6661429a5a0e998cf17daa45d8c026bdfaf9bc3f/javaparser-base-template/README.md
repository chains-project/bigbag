# Generic JavaParser Transformation for Breaking API Changes

This is a generic transformation tool designed to fix breaking API changes in Maven projects, particularly when upgrading Jenkins test harness dependencies or similar frameworks.

## Purpose

When Maven dependencies are updated, method signatures may change, causing compilation errors. This tool automatically detects common breaking changes and applies the necessary transformations to fix them.

## Usage

```bash
# Compile the tool
mvn compile

# Apply transformation to a project
java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) github.chains.Main /path/to/project/src
```

## Supported Transformations

1. **Adding boolean parameters**: When a method signature changes from `method(String)` to `method(String, boolean)`
2. **Adding null configuration parameters**: When a method signature changes from `method(String)` to `method(String, String)`

## Examples of Fixed Patterns

### Before:
```java
createPipelineJobWithScript("echo hello");
createPipelineJobInFolderWithScript(folder, "echo hello");
```

### After:
```java
createPipelineJobWithScript("echo hello", true);
createPipelineJobInFolderWithScript(folder, "echo hello", true);
```

## Customization

To add new patterns, extend the `ApiFixVisitor` class in `Main.java` with additional conditional logic in the `visit` method.

## Notes

- This tool operates on Java source files and modifies them in-place
- It should be used with caution and ideally with version control
- The transformation assumes reasonable defaults for new parameters