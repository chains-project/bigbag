# Generic JavaParser Transformation for Breaking Dependency Updates

This is a generic JavaParser-based transformation tool designed to fix breaking dependency updates in Maven projects.

## Purpose

When a dependency in a project is updated to a new version that introduces breaking changes (method signature changes, removed methods, etc.), this tool can automatically transform the code to adapt to the new API.

## Usage

```bash
java -jar javaparser-1.0-SNAPSHOT.jar <source-directory> <target-directory>
```

Where:
- `<source-directory>`: The directory containing the Java source files to transform
- `<target-directory>`: The directory where transformed files will be written

## Implementation Details

The transformation is built using JavaParser 3.27.1 and provides:
- A generic visitor pattern for traversing AST nodes
- Support for transforming method calls with breaking changes
- Proper handling of Java source files with encoding
- Directory traversal for batch processing

## Customization

To customize this transformation for specific breaking changes:
1. Modify the `BreakingChangeVisitor` class in `Main.java`
2. Override the `visit(MethodCallExpr n, Void arg)` method to handle specific patterns
3. Add logic to detect and transform specific method calls that changed in the dependency

## Example

For a breaking change where a method `oldMethod(String)` became `newMethod(String, boolean)`, you would:
1. Detect calls to `oldMethod` in the visitor
2. Transform them to use the new signature with appropriate default values

The transformation is designed to be reusable across different Maven projects with minimal modifications.