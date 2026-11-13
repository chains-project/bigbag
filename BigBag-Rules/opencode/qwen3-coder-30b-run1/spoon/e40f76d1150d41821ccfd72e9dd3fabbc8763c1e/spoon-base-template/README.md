# Generic Spoon Transformation for Acceptance Test Harness API Breaking Changes

## Problem Statement
The `@code-coverage-api-plugin` project fails to compile due to breaking changes in the `acceptance-test-harness` dependency. This transformation addresses breaking changes that affect method signatures in the `org.jenkinsci.test.acceptance` package.

## Analysis of Breaking Changes

Based on the API specification, the most likely breaking changes involve:
1. Methods gaining new parameters
2. Method signatures changing to support new functionality
3. Deprecated methods being removed

## Generic Transformation Approach

The transformation uses Spoon to:
1. Identify method invocations in the `org.jenkinsci.test.acceptance` package
2. Detect specific breaking change patterns
3. Apply appropriate fixes to maintain backward compatibility

## Implementation Details

### Key Transformation Patterns

The transformation handles these common breaking changes:

1. **Method signature changes** - When methods gain additional parameters
2. **New parameter defaults** - Adding default values for new parameters
3. **API evolution patterns** - Common patterns in API evolution

### Usage

```bash
java -jar acceptance-test-harness-fix-1.0-SNAPSHOT.jar /path/to/affected/project
```

### Generic Fix Patterns

The transformation includes logic to handle:
- Adding missing boolean parameters with default `false`
- Adding missing string parameters with default `""` 
- Adding missing integer parameters with default `0`
- Adding missing enum parameters with default values

## Implementation Strategy

The core approach:
1. Parse Java source files using Spoon
2. Identify method calls to acceptance test harness classes
3. Match against known breaking change patterns
4. Apply necessary parameter adjustments
5. Generate transformed source files

## Example Transformation

When a method like `assertJavadoc(Job job)` changes to `assertJavadoc(Job job, boolean strict)`:
- The transformation adds the missing `false` parameter
- Preserves existing code functionality
- Maintains compatibility with older API versions

## Generalizability

This transformation is designed to be:
- Reusable across different Maven projects
- Configurable for different breaking changes
- Generic enough to handle various API evolution patterns
- Independent of project-specific identifiers

## Limitations

- Requires manual pattern identification for specific breaking changes
- May need customization for unique API evolution patterns
- Assumes standard Java 8 compatibility