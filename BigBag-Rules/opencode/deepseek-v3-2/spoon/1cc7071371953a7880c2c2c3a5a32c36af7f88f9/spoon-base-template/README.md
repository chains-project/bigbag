# Generic Spoon Transformation Rule for assertj-core 3.23.0 ByteBuddy Breaking Change

## Overview

This is a **generic, reusable** Spoon transformation rule that fixes the breaking change in assertj-core 3.23.0 where ByteBuddy classes were moved from an internal package to an external dependency.

## Breaking Change Analysis

**Before assertj-core 3.23.0:**
- ByteBuddy classes were embedded in assertj-core under package: `org.assertj.core.internal.bytebuddy.*`

**After assertj-core 3.23.0:**
- ByteBuddy is now an external dependency
- Classes are available at: `net.bytebuddy.*`
- The internal package `org.assertj.core.internal.bytebuddy.*` was completely removed

## Transformation Rule Specification

**Pattern to match:**
- `org.assertj.core.internal.bytebuddy.*` (and all subpackages)

**Replacement:**
- `net.bytebuddy.*` (preserving the rest of the package/class name)

**Scope:**
- Import statements (regular and static)
- Type references in code
- Fully qualified class names

## Usage

### 1. Compile the transformation:
```bash
mvn compile
```

### 2. Run the transformation:
```bash
java -cp "target/classes:spoon-core.jar:dependencies/*" github.chains.Main <source-dir> [output-dir]
```

Where:
- `<source-dir>`: Source directory containing Java files to transform
- `[output-dir]`: Optional output directory (default: overwrites source)

### 3. Example:
```bash
# Transform a project in-place
java -cp "target/classes:..." github.chains.Main /path/to/project/src

# Transform to a different directory  
java -cp "target/classes:..." github.chains.Main /path/to/project/src /path/to/transformed/src
```

## What the Transformation Does

1. **AST-based transformation**: Uses Spoon to parse Java source files into an Abstract Syntax Tree (AST), finds all type references matching the old ByteBuddy package, and replaces them with the new package.

2. **File-based backup**: Also performs simple string replacement on import statements as a backup, since Spoon's import handling can sometimes miss transformations.

3. **Preserves code structure**: Maintains formatting, comments, and line numbers.

## Requirements for Transformed Projects

After applying this transformation, projects need to ensure ByteBuddy is available as a dependency:

```xml
<dependency>
    <groupId>net.bytebuddy</groupId>
    <artifactId>byte-buddy</artifactId>
    <version>1.12.10</version> <!-- or compatible version -->
</dependency>
```

Note: assertj-core 3.23.0+ brings ByteBuddy as a transitive dependency with version 1.12.10, so you may not need to add it explicitly unless you need a different version.

## Testing

The transformation was successfully tested on the `assertj-vavr` project:
- Before: 35 compilation errors due to missing ByteBuddy classes
- After: Compilation succeeds and all 694 tests pass

## Design Principles

This transformation is **generic and reusable**:
1. **No hardcoded project-specific identifiers**: Only transforms the package pattern
2. **Works on any Java project**: Not tied to assertj-vavr or any specific codebase
3. **Simple configuration**: Single package mapping defines the transformation
4. **Preserves code quality**: Maintains original code structure and formatting

## Extensibility

To adapt this transformation for similar breaking changes:

1. Update `PACKAGE_MAPPINGS` in `Main.java` with new old→new package mappings
2. Recompile and run on affected projects

## Limitations

- Only handles Java source files (`.java`)
- Requires Spoon dependencies at runtime
- May not handle reflection-based class loading (e.g., `Class.forName("org.assertj.core.internal.bytebuddy...")`)

## License

This transformation rule is provided as a template for fixing the assertj-core 3.23.0 ByteBuddy breaking change. Modify and use as needed for your projects.