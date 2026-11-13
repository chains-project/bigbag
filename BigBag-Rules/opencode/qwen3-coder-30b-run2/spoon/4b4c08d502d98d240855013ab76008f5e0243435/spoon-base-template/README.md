# Generic Flyway Breaking Change Transformation Rule

This is a generic Spoon transformation rule designed to fix breaking changes in Flyway dependencies, particularly when upgrading from older versions to newer ones (like from Flyway 8.x to 9.15.1).

## Problem Addressed

The transformation fixes cases where:
- Flyway's no-argument constructor was removed in favor of parameterized constructors
- Method signatures in the Flyway API have changed between versions
- Configuration approaches have been modified

## Usage

```bash
# Compile the transformation
mvn compile

# Run the transformation on the NEM project
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /workspace/nem /tmp/flyway-fix-output

# Or run with defaults
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main
```

## Key Features

1. **Generic Approach**: Works across different Flyway versions by detecting usage patterns
2. **Constructor Fix**: Transforms `new Flyway()` calls to `new Flyway(Flyway.configure())`
3. **Method Handling**: Identifies and processes Flyway method calls that may have changed
4. **Reusable**: Can be applied to any Maven project with Flyway dependencies

## How It Works

The transformation:
1. Scans Java source files for Flyway constructor calls (`new Flyway()`)
2. Detects no-argument constructor usage (common breaking change)
3. Replaces with parameterized constructor that uses `Flyway.configure()`
4. Identifies Flyway method calls for potential future enhancements

## Customization

To adapt this rule for specific breaking changes:
1. Modify the `fixNoArgFlywayConstructor` method for different constructor signatures
2. Extend `fixFlywayMethodCall` for specific method signature changes
3. Update the pattern matching filters if needed for different usage patterns

## Files Modified

- `src/main/java/github/chains/Main.java`: Core transformation logic
- `pom.xml`: Dependencies for Spoon and Java compilation