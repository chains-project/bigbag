# Generic JavaParser Transformation for Breaking Dependency Changes

This is a generic transformation rule that can be applied to fix breaking changes in dependencies across Maven projects.

## Purpose

This tool addresses the scenario where a Maven project experiences compilation errors due to breaking changes in a dependency. It provides a reusable framework that can be configured for different API changes without requiring code modifications for each specific breaking change.

## How It Works

The transformation uses JavaParser to:
1. Parse Java source files in a project
2. Identify method calls that match an old API pattern
3. Transform those calls to use the new API signature
4. Save the modified files back to disk

## Configuration

The transformation is designed to be configurable. Key parameters that can be customized for different breaking changes:

- `OLD_FULLY_QUALIFIED_METHOD_NAME`: The full qualified name of the old method (e.g., `org.example.Class.method`)
- `NEW_FULLY_QUALIFIED_METHOD_NAME`: The full qualified name of the new method 
- `OLD_PARAMETER_TYPES`: Array of old parameter types
- `NEW_PARAMETER_TYPES`: Array of new parameter types

## Usage

```bash
# Compile the transformation
mvn compile

# Run the transformation on a project directory
java -cp target/classes github.chains.Main /path/to/project/src
```

## Customization

To adapt this transformation for a specific breaking change:

1. Modify the configuration constants at the top of `Main.java`
2. Implement the `transformMethodCall()` method with specific transformation logic
3. Adjust the `matchesOldApiPattern()` method as needed for different matching criteria

## Limitations

This is a generic template. Specific implementations may require:
- More sophisticated pattern matching for complex APIs
- Handling of different types of AST nodes beyond method calls
- Support for more complex parameter transformations