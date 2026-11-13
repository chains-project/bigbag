# Generic JavaParser Transformation for Jenkins Dependency Breaking Changes

This project provides a generic JavaParser-based transformation tool that can fix breaking changes in Jenkins plugins caused by dependency updates, particularly focusing on the acceptance-test-harness library.

## Purpose

When Jenkins plugins depend on libraries like `acceptance-test-harness` and those libraries undergo breaking changes in their APIs, this tool can automatically fix common breaking changes in Java code.

## Usage

```bash
# Compile the transformation tool
mvn compile

# Run the transformation on a project
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) \
     github.chains.Main /path/to/your/project method-signature-change
```

## How It Works

The transformation works by:

1. Parsing Java source files using JavaParser
2. Identifying method calls that match specific breaking change patterns
3. Applying fixes to adapt to new API signatures
4. Saving the modified files back to disk

## Current Transformations

The current implementation supports:
- Method signature changes (adding/removing parameters)
- Parameter type conversions

## Extending for Specific Cases

To extend this transformation for specific breaking changes:

1. Modify the `MethodSignatureChangeTransformer` class
2. Add detection logic for your specific breaking change pattern
3. Add the corresponding fix logic

## Example Breaking Change

For example, if a method changed from:
```java
void oldMethod(String param1, String param2)
```
to:
```java
void newMethod(String param1, boolean param3, String param2)
```

The transformation would automatically add the new boolean parameter.

## Parameters

- `<source-directory>`: Path to the Java project to transform
- `<transformation-type>`: Type of transformation to apply (currently supports `method-signature-change`)

## Limitations

This is a generic template and may need customization for specific breaking changes. It works best for predictable API changes with clear patterns.