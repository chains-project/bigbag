# Generic JavaParser Transformation Rule for API Breaking Changes

## Overview

This JavaParser-based transformation tool provides a generic solution for fixing compilation errors caused by breaking API changes in dependencies. The tool can be easily configured to handle various types of API breaking changes.

## Problem Statement

When a library releases a breaking change (e.g., okio 3.4.0 changing method signatures), client projects that depend on the old API will fail to compile. Manually fixing these issues across a large codebase is error-prone and time-consuming.

## Solution Architecture

The transformation tool uses JavaParser to:
1. Parse Java source files
2. Identify method calls that match old API patterns
3. Transform them to match new API patterns
4. Write the transformed code back to files

## Configuration

The tool is configured through the `METHOD_TRANSFORMATIONS` map in `Main.java`:

```java
// Key: method name
// Value: map of old argument count to list of new argument expressions
private static final Map<String, Map<Integer, List<String>>> METHOD_TRANSFORMATIONS = new HashMap<>();
```

### Example Configurations

#### 1. Adding Required Parameters
When a method adds new required parameters:

```java
// Old: write(ByteString)
// New: write(ByteString, offset, byteCount)
Map<Integer, List<String>> writeTransformations = new HashMap<>();
List<String> newArgs = new ArrayList<>();
newArgs.add("0");  // offset
newArgs.add("byteString.size()");  // byteCount
writeTransformations.put(1, newArgs);
METHOD_TRANSFORMATIONS.put("write", writeTransformations);
```

#### 2. Adding Optional Parameters with Default Values
When a method adds optional parameters:

```java
// Old: writeUtf8(String)
// New: writeUtf8(String, charset)
Map<Integer, List<String>> writeUtf8Transformations = new HashMap<>();
List<String> newUtf8Args = new ArrayList<>();
newUtf8Args.add("java.nio.charset.StandardCharsets.UTF_8");
writeUtf8Transformations.put(1, newUtf8Args);
METHOD_TRANSFORMATIONS.put("writeUtf8", writeUtf8Transformations);
```

#### 3. Removing Parameters
When a method removes parameters:

```java
// Old: oldMethod(String, int)
// New: oldMethod(String)
Map<Integer, List<String>> removeParamTransformations = new HashMap<>();
removeParamTransformations.put(2, new ArrayList<>()); // Empty list removes all args
METHOD_TRANSFORMATIONS.put("oldMethod", removeParamTransformations);
```

#### 4. Method Renaming
For method name changes, you would need to extend the visitor:

```java
@Override
public Visitable visit(MethodCallExpr n, Void arg) {
    MethodCallExpr methodCall = (MethodCallExpr) super.visit(n, arg);
    
    if (methodCall.getNameAsString().equals("oldMethodName")) {
        methodCall.setName("newMethodName");
    }
    
    return methodCall;
}
```

## Usage

### Building
```bash
cd /workspace/javaparser-base-template
mvn compile
```

### Running
```bash
cd /workspace/javaparser-base-template
java -cp "target/classes:$(find ~/.m2/repository -name '*.jar' 2>/dev/null | tr '\n' ':')" github.chains.Main <source-directory>
```

Example:
```bash
java -cp "target/classes:$(find ~/.m2/repository -name '*.jar' 2>/dev/null | tr '\n' ':')" github.chains.Main /workspace/facebook-java-business-sdk/src/main/java
```

## Extending for Complex Transformations

For more complex transformations (type checking, conditional logic, etc.), extend the `ApiTransformationVisitor` class:

1. **Type-based transformations**: Use JavaParser's type resolution to check argument types
2. **Context-aware transformations**: Check the surrounding code context
3. **Multiple transformation rules**: Chain transformations for complex migrations
4. **Import handling**: Automatically add required imports for new types

## Limitations and Future Improvements

### Current Limitations:
1. Simple string-based argument matching (doesn't check types)
2. No import handling for new types
3. No support for constructor changes
4. No support for field access changes

### Future Improvements:
1. Add type resolution for precise matching
2. Support for constructor transformations
3. Support for field access transformations
4. Configuration via external files (JSON/YAML)
5. Dry-run mode to preview changes
6. Undo/rollback capability

## Real-world Example: okio 3.4.0 Breaking Changes

Based on common breaking changes in okio:

1. **Method signature changes**: `write(ByteString)` → `write(ByteString, offset, byteCount)`
2. **Charset parameters**: `writeUtf8(String)` → `writeUtf8(String, charset)`
3. **Nullability changes**: Parameters might become nullable/non-nullable
4. **Kotlin interoperability**: Kotlin-specific API changes affecting Java usage

## Testing the Transformation

1. Create test cases with before/after code samples
2. Run the transformation on test files
3. Verify the output matches expected results
4. Compile the transformed code to ensure no errors

## Contributing

To add new transformation patterns:
1. Identify the old and new API patterns
2. Add configuration to `METHOD_TRANSFORMATIONS`
3. Test with sample code
4. Update documentation