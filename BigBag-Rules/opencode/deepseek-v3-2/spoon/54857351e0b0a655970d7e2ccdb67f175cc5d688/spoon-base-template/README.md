# Generic Package Relocation Transformation

This tool provides a generic transformation to fix breaking changes where a class moves from one package to another.

## Example: zip4j 2.10.0 Breaking Change

**Problem**: zip4j 2.10.0 moved the `ZipFile` class from package `net.lingala.zip4j.core` to `net.lingala.zip4j`.

**Breaking Change Pattern**:
- **Old API**: `net.lingala.zip4j.core.ZipFile`
- **New API**: `net.lingala.zip4j.ZipFile`
- **Transformation Required**: Update import statements and type references

## How It Works

The transformation performs the following:

1. **Import Statements Update**: Changes `import net.lingala.zip4j.core.ZipFile;` to `import net.lingala.zip4j.ZipFile;`
2. **Type References Update**: Changes any fully-qualified references from `net.lingala.zip4j.core.ZipFile` to `net.lingala.zip4j.ZipFile`
3. **Code References Update**: Updates any `new ZipFile(...)` or `ZipFile.` method calls (Java resolves these via imports)

## Usage

1. **Compile the transformation**:
   ```bash
   mvn clean compile
   ```

2. **Run on a project**:
   ```bash
   java -cp target/classes github.chains.Main /path/to/project/src
   ```

3. **Verify the fix**:
   ```bash
   cd /path/to/project
   mvn compile
   mvn test
   ```

## Making It Generic for Other Breaking Changes

To adapt this transformation for other package relocation issues:

1. **Update the constants in `Main.java`**:
   ```java
   // Configuration: Old and new package/class names
   private static final String OLD_PACKAGE = "original.package.name";
   private static final String OLD_CLASS_NAME = "ClassName";
   private static final String NEW_PACKAGE = "new.package.name";
   private static final String NEW_CLASS_NAME = "ClassName"; // Usually same
   ```

2. **Recompile and run**:
   ```bash
   mvn clean compile
   java -cp target/classes github.chains.Main /path/to/project
   ```

## What This Transformation Handles

✅ **Simple package relocation**: `com.old.pkg.Class` → `com.new.pkg.Class`
✅ **Import statements**: Direct imports of the moved class
✅ **Fully-qualified references**: Inline references like `new com.old.pkg.Class()`
✅ **Static method calls**: References like `com.old.pkg.Class.staticMethod()`

## Limitations

❌ **Wildcard imports**: `import com.old.pkg.*;` needs manual update
❌ **Reflection usage**: `Class.forName("com.old.pkg.Class")` needs manual update
❌ **String constants**: Hardcoded strings with class names need manual update
❌ **Annotations**: Annotation values with class names need manual update

## Testing the Transformation

After running the transformation:
1. Ensure the project compiles without errors
2. Run tests to verify functionality
3. Check for any runtime issues related to reflection or dynamic class loading

## Example Output

```
Applying package relocation transformation to: /workspace/allure-maven
Transformation: net.lingala.zip4j.core.ZipFile -> net.lingala.zip4j.ZipFile
Updated: /workspace/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java
  - Import statements: 1
  - Type references: 1

Transformation completed:
  Files updated: 2
  Total replacements: 6
```

## Dependencies

- Java 17+
- Maven (for building)
- No external dependencies for the transformation itself