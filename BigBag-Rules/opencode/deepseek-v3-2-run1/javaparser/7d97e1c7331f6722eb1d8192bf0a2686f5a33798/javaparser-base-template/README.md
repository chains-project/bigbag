# Generic JavaParser Transformation for Breaking Dependency Changes

This tool provides a generic transformation rule to fix compilation errors caused by breaking dependency updates where a utility class with constants has been removed.

## Problem
When a dependency removes a utility class (e.g., `com.jcabi.aspects.Tv`), client code that uses constants from that class (`Tv.TEN`, `Tv.EIGHT`, etc.) will fail to compile.

## Solution
This JavaParser-based transformation:
1. Removes imports of the specified class
2. Replaces field accesses like `ClassName.CONSTANT` with appropriate literal values

## Usage
```bash
java -cp "target/classes:target/dependency/*" github.chains.Main <source-directory> <fully-qualified-class-name>
```

Example:
```bash
java -cp "target/classes:target/dependency/*" github.chains.Main /path/to/project/src com.jcabi.aspects.Tv
```

## How It Works
1. **Import Removal**: Scans all Java files and removes imports of the specified class
2. **Constant Replacement**: Replaces field accesses like `Tv.TEN` with numeric literals based on constant name mapping
   - `Tv.TEN` → `10`
   - `Tv.EIGHT` → `8`
   - `Tv.HUNDRED` → `100`
   - etc.

## Supported Constant Names
The transformation includes a mapping for common numeric constant names:
- Basic numbers: ZERO, ONE, TWO, ..., TEN
- Multiples: TWENTY, THIRTY, ..., NINETY
- Powers: HUNDRED, THOUSAND, MILLION

Unknown constants are replaced with `0` (with a warning).

## Limitations
1. Only handles integer constants
2. Assumes constants are accessed via simple class name (not fully qualified)
3. Doesn't handle static imports
4. Doesn't handle method calls or other uses of the class

## Extending
To add support for more constant types or patterns:
1. Extend the `CONSTANT_VALUES` map in `Main.java`
2. Modify `FieldAccessReplacer` to handle different expression types
3. Add support for static imports by checking `ImportDeclaration.isStatic()`

## Building
```bash
mvn clean compile
mvn dependency:copy-dependencies
```

## Testing
The transformation was tested on `jcabi-simpledb` project which had compilation errors due to removal of `com.jcabi.aspects.Tv` in jcabi-aspects 0.25.1.