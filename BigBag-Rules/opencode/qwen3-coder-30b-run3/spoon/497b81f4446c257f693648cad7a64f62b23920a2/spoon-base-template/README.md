# Generic Spoon Transformation for Breaking API Changes

## Purpose
This repository contains a reusable transformation template for fixing breaking dependency updates in Maven projects. The template is designed to be adaptable for any breaking API change.

## Structure
The transformation is implemented in `src/main/java/github/chains/Main.java` and follows these principles:

1. **Generic Pattern Matching**: Uses Spoon's AST traversal to find problematic patterns
2. **Reusable Framework**: Can be easily adapted for different breaking changes
3. **Safe Application**: Only modifies identified patterns to preserve code integrity

## How to Use

### For a Specific Breaking Change:

1. Identify the breaking change pattern (e.g., constructor signature change)
2. Modify the pattern matching in `main()` method to detect your specific issue
3. Implement the fix logic in the appropriate handler method
4. Run the transformation against your target project

### Example Usage Pattern:
```java
// Find all instances of the problematic API
List<CtConstructorCall<?>> calls = model.getElements(...);

// Apply transformation
for (CtConstructorCall<?> call : calls) {
    fixSpecificApiCall(call, factory);
}
```

## Specific Example: Headers.From Constructor Changes
Based on the HTTP API specification, the `Headers.From` class has had constructor signature changes. This template shows how to:
1. Locate all `Headers.From` constructor calls
2. Analyze their parameters
3. Apply necessary transformations

## Key Files

- `src/main/java/github/chains/Main.java` - Core transformation logic
- `README.md` - This documentation

## Template Adaptation

To adapt this for your specific breaking change:

1. **Update pattern matching**: Change the `TypeFilter` conditions to match your API
2. **Implement fix logic**: Fill in `fixSpecificApiCall()` with transformation code
3. **Test thoroughly**: Run on sample code before applying to production projects

## Benefits of This Approach

1. **Reusable**: Can be applied to any Maven project with breaking changes
2. **Automated**: Eliminates manual patching of multiple files
3. **Consistent**: Ensures all instances are fixed identically
4. **Safe**: Only transforms identified problematic patterns