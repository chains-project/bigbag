# Generic JavaParser Transformation for Breaking Dependency Changes

## Overview
This JavaParser-based transformation tool automatically fixes compilation errors caused by breaking changes in dependencies, specifically when a class with static constants is removed.

## Problem Statement
When a dependency removes a class that contains static constants (e.g., `com.jcabi.aspects.Tv` with constants like `Tv.TEN`, `Tv.HUNDRED`), all client code that references these constants fails to compile. This tool provides a generic solution to:
1. Remove imports of the deprecated class
2. Replace references to the class constants with their literal values

## Generic Transformation Pattern
- **Old Pattern**: `ClassName.CONSTANT_NAME` (e.g., `Tv.TEN`)
- **New Pattern**: Literal value (e.g., `10`)
- **Transformation**: Map each `ClassName.CONSTANT` to its corresponding literal value

## Configuration
The transformation is configured through three constants in `Main.java`:

```java
// The fully-qualified class name to remove
private static final String CLASS_TO_REMOVE = "com.jcabi.aspects.Tv";

// The simple class name (used in field access expressions)
private static final String CLASS_SIMPLE_NAME = "Tv";

// Mapping of constant names to their replacement values
private static final Map<String, String> CONSTANT_MAPPING = new HashMap<>();
```

### Example Configuration for Tv Class
```java
static {
    CONSTANT_MAPPING.put("ONE", "1");
    CONSTANT_MAPPING.put("TWO", "2");
    CONSTANT_MAPPING.put("TEN", "10");
    CONSTANT_MAPPING.put("TWENTY", "20");
    CONSTANT_MAPPING.put("HUNDRED", "100");
    // ... add all known constants
}
```

## Usage
1. Configure the transformation by editing `CLASS_TO_REMOVE`, `CLASS_SIMPLE_NAME`, and `CONSTANT_MAPPING`
2. Compile: `mvn compile`
3. Run: `java -cp "target/classes:$(find ~/.m2/repository -name '*.jar' 2>/dev/null | tr '\n' ':')" github.chains.Main <source-directory>`

## How It Works
1. **Import Removal**: Scans for and removes imports of `CLASS_TO_REMOVE`
2. **Field Access Replacement**: Finds all occurrences of `ClassName.CONSTANT` and replaces them with mapped literal values
3. **Safe Modification**: Only writes files back to disk if changes were actually made

## Extending for Other Breaking Changes
This pattern can be adapted for other breaking changes:
- Change `IntegerLiteralExpr` to `StringLiteralExpr` for string constants
- Change `IntegerLiteralExpr` to other expression types as needed
- Modify the visitor to handle different AST patterns

## Example: jcabi-aspects 0.25.1 Breaking Change
For the specific case of `jcabi-aspects` 0.25.1 removing the `Tv` class:
- `CLASS_TO_REMOVE = "com.jcabi.aspects.Tv"`
- `CLASS_SIMPLE_NAME = "Tv"`
- `CONSTANT_MAPPING` contains English number names to integer values

## Requirements
- Java 11+
- Maven
- JavaParser 3.27.1+

## Limitations
- Only handles static field access expressions (`ClassName.CONSTANT`)
- Requires manual configuration of the constant mapping
- Assumes constants are replaced with integer literals (modify for other types)