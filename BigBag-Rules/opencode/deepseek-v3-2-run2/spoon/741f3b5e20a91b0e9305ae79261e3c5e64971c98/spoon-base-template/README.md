# Correct Spoon Transformation for Jackson Exception Migration

This project provides a corrected Spoon transformation for migrating Jackson exception types from version 2.12 and earlier to 2.13+.

## The Problem

When upgrading Jackson from 2.12 to 2.13+, two exception types were moved:
1. `com.fasterxml.jackson.core.JsonParseException` → `com.fasterxml.jackson.core.exc.StreamReadException`
2. `com.fasterxml.jackson.databind.JsonMappingException` → `com.fasterxml.jackson.databind.DatabindException`

## Common Errors in Spoon Code

### Error 1: `setQualifiedName()` doesn't exist
**Wrong approach:**
```java
// This doesn't work - setQualifiedName() doesn't exist on CtTypeReference
ref.setQualifiedName("com.fasterxml.jackson.core.exc.StreamReadException");
```

**Correct approach:**
```java
// Create a new type reference and replace the old one
CtTypeReference<?> newRef = factory.createReference("com.fasterxml.jackson.core.exc.StreamReadException");
ref.replace(newRef);
```

### Error 2: Type mismatch for `setThrownTypes()`
**Wrong approach:**
```java
// Type mismatch - List<CtTypeReference<?>> doesn't match expected type
method.setThrownTypes(newThrownTypes);
```

**Correct approach:**
```java
// Use proper generic type <? extends Throwable>
List<CtTypeReference<? extends Throwable>> newThrownTypes = new ArrayList<>();
// ... populate list ...
method.setThrownTypes(newThrownTypes);
```

## Key Fixes in the Solution

1. **Use `replace()` instead of `setQualifiedName()`** for updating type references
2. **Use proper generic types** (`<? extends Throwable>`) for thrown exception lists
3. **Process ALL type references** not just specific contexts (imports are handled automatically)
4. **Handle multiple contexts**: variable types, return types, field types, throws clauses, catch blocks

## How to Use

### Build the transformation:
```bash
cd /workspace/spoon-base-template
./build.sh
```

### Run the transformation:
```bash
# Modify files in-place
java -jar target/jackson-exception-migration.jar /path/to/source/code

# Or specify output directory
java -jar target/jackson-exception-migration.jar /path/to/source/code /path/to/output
```

## What Gets Updated

The transformation updates:
- Import statements
- Variable declarations
- Field declarations
- Method return types
- Method/constructor throws clauses
- Catch block exception types
- Multi-catch expressions
- Type casts and instanceof checks

## Example Transformation

**Before:**
```java
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class Example {
    public void readData() throws JsonParseException, JsonMappingException {
        try {
            // ... code ...
        } catch (JsonParseException e) {
            // handle
        }
    }
    
    private JsonMappingException lastError;
}
```

**After:**
```java
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;

public class Example {
    public void readData() throws StreamReadException, DatabindException {
        try {
            // ... code ...
        } catch (StreamReadException e) {
            // handle
        }
    }
    
    private DatabindException lastError;
}
```

## Files in This Project

- `CorrectJacksonExceptionMigration.java` - Main corrected transformation
- `FixedJacksonExceptionTransformer.java` - Alternative implementation
- `JacksonExceptionTransformer.java` - More robust implementation
- `TestJacksonExceptions.java` - Example Java file for testing
- `build.sh` - Build script
- `pom.xml` - Maven configuration with Spoon 11.2.1