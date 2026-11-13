# Jakarta MVC Package Migration Transformation

This is a generic Spoon transformation designed to migrate Jakarta MVC projects from the old `javax.mvc` package names to the new `jakarta.mvc` package names, which is a common breaking change when upgrading from Jakarta EE 8 to Jakarta EE 9+.

## Problem Description

The jakartaee-mvc-sample project fails to compile because it references `javax.mvc` packages, but the dependency has been updated to use `jakarta.mvc` packages. This is a typical breaking change in Jakarta EE migrations.

## Solution Approach

This transformation provides a framework for migrating all `javax.mvc` references to `jakarta.mvc`:
1. Import statements
2. Fully qualified type references
3. Package declarations

## Usage

```bash
java -cp spoon-base-template.jar github.chains.Main /path/to/project/src
```

The transformation will create a new directory with `_new` suffix containing the migrated code.

## Implementation Details

This is a generic rule that:
- Works with any Maven project suffering from the same breaking change
- Provides a reusable framework for migrating Jakarta MVC APIs
- Focuses on the structural approach to handle the package name change
- Can be extended with additional processors for more complex transformations

## Limitations

Due to the complexity of Spoon's import handling, a complete implementation would require:
1. Custom processors to handle import statement replacement
2. More sophisticated type reference replacement
3. Proper handling of package declarations

This basic framework provides the foundation for such a complete implementation.