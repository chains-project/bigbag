# Generic JavaParser Transformation for jcabi-aspects Breaking Changes

This is a generic transformation rule designed to fix breaking dependency updates in Maven projects that use jcabi-aspects.

## Problem Description

When jcabi-aspects dependency is updated (specifically to version 0.25.1), projects using annotations like `@Loggable`, `@Cacheable`, etc. may encounter compilation errors due to breaking API changes.

## Solution Overview

This transformation targets common breaking changes in jcabi-aspects annotations, particularly:
- Renaming of annotation parameter names (e.g., `level` → `value`)
- Changes in default parameter values
- Removal or deprecation of certain attributes

## Usage

```bash
# Transform a Maven project
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/project/src/main/java /path/to/output/dir
```

## Supported Transformations

1. **Loggable Annotation Fixes**:
   - Converts `@Loggable(level = Loggable.DEBUG)` to `@Loggable(value = Loggable.DEBUG)`
   - Adds default value for empty Loggable annotations

2. **Other Aspects Annotations**:
   - Generic handler for common patterns that might change between versions

## Customization

To adapt this transformation for other projects or different breaking changes:
1. Modify the `transformAspectsAnnotations` method
2. Add new patterns to detect and fix specific breaking changes
3. Update the input/output paths accordingly

## Limitations

This is a generic transformation that addresses known patterns of breaking changes. For specific projects, additional custom transformations may be required.