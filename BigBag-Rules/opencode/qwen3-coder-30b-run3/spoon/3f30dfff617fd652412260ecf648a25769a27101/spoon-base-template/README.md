# Jakarta Interceptor API Migration Tool

This is a Spoon-based transformation tool designed to fix breaking changes in the Jakarta Interceptor API when migrating from older versions (typically using `javax.interceptor`) to Jakarta EE 9+ (which uses `jakarta.interceptor`).

## Key Breaking Changes Addressed

1. **Package Name Changes**: Migration from `javax.interceptor` to `jakarta.interceptor`
2. **Import Statement Updates**: All import declarations referencing old packages are corrected
3. **Type Reference Fixes**: All type references within the codebase are updated

## How It Works

The tool uses the Spoon library to parse Java source code and automatically:
- Identify all references to `javax.interceptor` packages
- Replace them with `jakarta.interceptor` equivalents
- Generate corrected files in a new directory

## Usage

### Prerequisites
- Java 17+
- Maven project with Spoon dependency (included in pom.xml)

### Running the Tool

```bash
# Compile the project first
mvn compile

# Run the transformation on your Java source code
java -cp target/classes github.chains.Main /path/to/your/source/code
```

### Example Usage

```bash
# Transform a test directory
java -cp target/classes github.chains.Main src/test/resources/test-code

# The fixed files will be generated in src/test/resources/test-code-fixed
```

## What Gets Fixed

When the tool processes code with references like:

```java
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@AroundInvoke
public class MyInterceptor {
    // ...
}
```

It transforms them to:

```java
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@AroundInvoke
public class MyInterceptor {
    // ...
}
```

## Limitations

This tool addresses the most common breaking changes related to namespace migration. For complex cases involving:
- Method signature changes 
- Annotation modifications
- Behavioral changes

Additional manual review may be required.

## Technical Details

The transformation works by:
1. Parsing Java source files using Spoon's AST
2. Identifying all `CtTypeReference` elements starting with `javax.interceptor`
3. Updating their qualified names to use `jakarta.interceptor` instead
4. Generating corrected files with the same directory structure

## Requirements

- Java 17+
- Maven 3.6+
- Spoon library (version 11.2.1, included in dependencies)

## License

This tool is provided as-is for educational purposes and demonstrates how to use Spoon for automated refactoring.