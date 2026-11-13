# Generic Spoon Transformation for Mockito 5.x Breaking Change

## Breaking Change Analysis

**Old API Pattern:** `org.mockito.invocation.InvocationOnMock.getArgumentAt(int index, Class<T> type)`

**New API Pattern:** `org.mockito.invocation.InvocationOnMock.getArgument(int index, Class<T> type)`

**Structural Transformation Required:** Simple rename of method call from `getArgumentAt` to `getArgument` with identical parameters.

## Transformation Rule

The Spoon transformation in `Main.java` performs the following:

1. **Pattern Matching:** Searches for all method invocations with name `getArgumentAt`
2. **Transformation:** Replaces `invocation.getArgumentAt(index, type)` with `invocation.getArgument(index, type)`
3. **Generality:** The transformation is generic and applies to any project using Mockito where `getArgumentAt` is called on any type (including subtypes of `InvocationOnMock`)

## Usage

```bash
java -cp "target/classes:dependency-classpath" github.chains.Main <source_directory> <output_directory>
```

## Example

For the bval project:
```bash
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" github.chains.Main /workspace/bval /workspace/transformed-bval
```

## Code Structure

The transformation:
1. Uses Spoon's `CtModel` to parse and build an AST of the source code
2. Applies a `TypeFilter` to find all `CtInvocation` elements calling `getArgumentAt`
3. Creates a new method reference with name `getArgument` instead of `getArgumentAt`
4. Replaces the old invocation with the new one while preserving arguments and target

## Dependencies

- Spoon Core 11.2.1
- Java 17+

## Notes

- This transformation handles both single-argument `getArgumentAt(index)` and two-argument `getArgumentAt(index, type)` calls
- The transformation preserves all other aspects of the method call (arguments, type parameters, etc.)
- Works with any project structure as it processes all Java files in the input directory