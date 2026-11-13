# JavaParser Mockito 3.x to 4.x Transformation Tool

A JavaParser-based tool for automatically identifying and transforming Mockito 3.x patterns to Mockito 4.x compatibility.

## Features

1. **Import Transformation**: Updates `org.mockito.runners.MockitoJUnitRunner` to `org.mockito.junit.MockitoJUnitRunner`
2. **Static Mocking Detection**: Identifies static method mocking patterns and adds `MockedStatic` import
3. **Pattern Detection**: Warns about `verifyStatic()` usage that requires manual transformation
4. **Generic and Reusable**: Works on any Java project directory

## Requirements

- Java 11+
- Maven 3.6+
- JavaParser 3.27.1

## Building

```bash
mvn clean package
```

## Usage

```bash
java -jar target/javaparser-1.0-SNAPSHOT.jar <source-directory>
```

Example:
```bash
java -jar target/javaparser-1.0-SNAPSHOT.jar /path/to/your/project/src
```

## Transformations Applied

### Automatic:
- `import org.mockito.runners.MockitoJUnitRunner;` → `import org.mockito.junit.MockitoJUnitRunner;`
- Adds `import org.mockito.MockedStatic;` when static mocking patterns detected

### Warnings Generated:
- Static method mocking with `when()` requires `mockStatic()` in Mockito 4.x
- `verifyStatic()` requires transformation to try-with-resources pattern

## Examples

### Before (Mockito 3.x):
```java
import org.mockito.runners.MockitoJUnitRunner;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestClass {
    @Test
    public void testStaticMethod() {
        when(Utility.staticMethod()).thenReturn("mocked");
    }
}
```

### After Transformation:
```java
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.Mockito.when;
import org.mockito.MockedStatic;

@RunWith(MockitoJUnitRunner.class)
public class TestClass {
    @Test 
    public void testStaticMethod() {
        when(Utility.staticMethod()).thenReturn("mocked");
        // Warning: Requires manual transformation to:
        // try (MockedStatic<Utility> mocked = mockStatic(Utility.class)) {
        //   mocked.when(Utility::staticMethod).thenReturn("mocked");
        // }
    }
}
```

## Implementation Details

The tool uses:
- JavaParser 3.27.1 for AST parsing and modification
- `StaticJavaParser.parse()` for modern API usage  
- `ModifierVisitor` for AST traversal and transformation
- `DefaultPrinterConfiguration` for code formatting

## Limitations

1. **Static method mocking**: Only detects patterns, requires manual code restructuring
2. **verifyStatic()**: Detects usage but doesn't automatically transform
3. **Complex expressions**: May not detect all static mocking patterns

The tool is designed as a first-pass helper to identify and perform simple transformations, with manual review needed for complex cases.