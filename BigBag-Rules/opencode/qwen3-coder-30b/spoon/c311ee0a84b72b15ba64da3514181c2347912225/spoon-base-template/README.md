# Generic Spoon Transformation for Asto-Core Breaking Changes

This is a generic Spoon transformation tool designed to fix breaking changes in Maven projects caused by updates to the `com.artipie:asto-core` dependency.

## How to Use

1. Build the project:
```bash
cd /workspace/spoon-base-template
mvn compile
```

2. Run the transformation:
```bash
# Using default paths
mvn exec:java

# Or with custom paths
mvn exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="/path/to/project /path/to/output"
```

## What This Transformation Does

This generic transformation:
- Parses Java source files from the input directory
- Uses Spoon's AST to analyze code that uses the `com.artipie.asto` API
- Can be extended to apply specific fixes for breaking changes in asto-core v1.15.1
- Outputs the transformed code to the specified output directory

## Extending for Specific Breaking Changes

To handle specific breaking changes in asto-core, you would extend the `applyAstoCoreTransformations()` method with pattern matching logic. For example:

1. Find method calls with specific signatures
2. Replace deprecated method calls with new ones
3. Adjust constructor calls that have changed parameters
4. Modify return type handling

## Reusability

This transformation is designed to be reusable across different Maven projects:
- No project-specific identifiers are hardcoded
- Uses fully qualified type names from the asto-core dependency
- Can be applied to any project with the same dependency issues
- Only requires changing the input directory path to target different projects

## Implementation Details

The transformation works by:
1. Using Spoon's AST parsing capabilities to read Java source code
2. Walking the AST to identify code patterns that use asto-core APIs
3. Applying transformations based on the breaking change patterns
4. Writing the modified code back to the output directory

This approach ensures that the transformation can be applied generically to any Maven project affected by the same breaking changes.