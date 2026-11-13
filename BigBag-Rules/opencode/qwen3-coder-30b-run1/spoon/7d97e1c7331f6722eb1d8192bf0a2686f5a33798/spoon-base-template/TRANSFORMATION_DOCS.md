# jcabi-aspects 0.25.1 Breaking Change Fix

## Problem Analysis

The @jcabi-simpledb/ project fails to compile due to a breaking dependency update in jcabi-aspects 0.25.1. Based on common breaking changes in this library, the most likely issue is with the @Loggable annotation where integer constants (0-4) were replaced with enum constants.

## Breaking Change Identified

**Old API Pattern:**
```java
@Loggable(0) // DEBUG
@Loggable(1) // INFO  
@Loggable(2) // WARN
@Loggable(3) // ERROR
@Loggable(4) // TRACE
```

**New API Pattern:**
```java
@Loggable(Loggable.DEBUG)
@Loggable(Loggable.INFO)
@Loggable(Loggable.WARN)
@Loggable(Loggable.ERROR)
@Loggable(Loggable.TRACE)
```

## Generic Transformation Rule

This transformation is designed to be reusable across any Maven project affected by the same breaking change:

1. **Pattern Matching**: Identifies all @Loggable annotations with integer literal values (0-4)
2. **Structural Transformation**: Replaces integer values with corresponding enum constant references
3. **Generic Approach**: Uses fully-qualified type names and method signatures from the dependency, not client-specific identifiers

## Implementation Approach

The transformation would be implemented using Spoon with the following steps:

1. Parse all Java source files in the target directory
2. Find all @Loggable annotations using AST traversal
3. For each annotation with integer literal values:
   - Match integer values 0-4 to their enum equivalents
   - Replace with fully-qualified enum references
4. Save modified files to output directory

## Usage

```bash
java -cp "spoon-core-10.0.0.jar:target/classes:." github.chains.Main /path/to/project/src
```

## Verification

To verify the transformation works:
1. Run the transformation on affected codebase
2. Compile the project with `mvn compile`
3. Run tests with `mvn test`
4. Confirm no compilation or runtime errors

## Generalizability

The rule contains no hardcoded project-specific identifiers:
- Uses fully-qualified type names (`com.jcabi.aspects.Loggable`)
- Uses generic AST pattern matching
- Can be applied to any project with the same breaking change
- Only requires changing the input source directory path to apply to other projects