# Generic Spoon Transformation for Breaking API Changes

This project provides a generic framework for automatically fixing breaking API changes in Maven projects using Spoon.

## Purpose

When a dependency updates and introduces breaking API changes, this transformation can be used to automatically migrate code from the old API to the new API across any Maven project.

## Usage

```bash
java -cp target/classes:target/dependency/* github.chains.Main <input-directory> <output-directory>
```

Where:
- `<input-directory>`: Path to the source code to transform
- `<output-directory>`: Path where transformed code will be saved

## How It Works

1. **AST Parsing**: Uses Spoon to parse Java source files into an Abstract Syntax Tree (AST)
2. **Pattern Matching**: Identifies method invocations and API usages that match known breaking patterns
3. **Transformation**: Applies generic transformations to migrate to new APIs
4. **Code Generation**: Outputs transformed source code to the specified directory

## Customization

To customize this transformation for a specific breaking change:

1. Modify the `handleBreakingChange()` method to detect your specific breaking API pattern
2. Implement the appropriate transformation logic for that pattern
3. Add specific rules for method signature changes, parameter additions/removals, etc.

## Implementation Details

The transformation works by:
- Scanning all method invocations in the codebase
- Identifying patterns that match breaking API signatures
- Applying appropriate replacements based on the breaking change

## Requirements

- Java 17+
- Maven 3.6+
- Spoon 11.2.1

## Extending for Specific Use Cases

To extend this for a specific breaking change:

1. Add detection logic in `handleBreakingChange()`
2. Implement specific transformation rules
3. Parameterize the transformation with the actual breaking change details
4. Use the `CtInvocation` object to inspect and modify method calls

## Example Extension

```java
private static void handleBreakingChange(CtInvocation<?> invocation) {
    CtExecutableReference<?> executableRef = invocation.getExecutable();
    String signature = executableRef.getSignature();
    
    // Specific breaking change detection
    if (signature.equals("org.example.OldClass.oldMethod(java.lang.String)")) {
        // Transform to new API
        // invocation.replace(newInvocation); 
    }
}
```

## Best Practices

1. Always test transformations on a copy of your codebase
2. Review transformed code manually before committing
3. Maintain version control to compare changes
4. Document specific breaking changes being addressed