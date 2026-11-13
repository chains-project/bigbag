# Generic ASM API Transformation Rule

This project provides a reusable transformation rule for fixing breaking changes in ASM library APIs, specifically targeting `visitAnnotation` method signature changes.

## Problem
Dependency updates often introduce breaking changes to APIs. In this case, the `pitest-mutation-testing-elements-plugin` was failing to compile due to breaking changes in the ASM library's `visitAnnotation` method signature.

## Solution
This transformation provides a generic mechanism to detect and fix ASM API breaking changes by:

1. Identifying calls to `visitAnnotation` methods with incorrect signatures
2. Applying transformations to align with the new API requirements
3. Making the fix reusable across multiple projects

## Usage
```bash
# Compile the transformation
mvn compile

# Run the transformation on a project
java -cp target/classes github.chains.ASMVisitorFixTransformer
```

## Implementation Details
The transformation works by:
- Parsing Java source files with Spoon
- Locating `visitAnnotation` method calls
- Detecting patterns matching known breaking changes
- Applying appropriate transformations to fix the signatures

## Target Fix
The specific transformation addresses changes like:
- Old signature: `visitAnnotation(String name, String desc)`
- New signature: `visitAnnotation(String name, boolean visible)`

## Reusability
This transformation is designed to be applicable to any project using ASM where similar breaking changes occur. Simply change the input source directory path in the code to target a different project.

## Limitations
This is a template implementation that needs to be extended with specific detection and transformation logic for each breaking change identified.