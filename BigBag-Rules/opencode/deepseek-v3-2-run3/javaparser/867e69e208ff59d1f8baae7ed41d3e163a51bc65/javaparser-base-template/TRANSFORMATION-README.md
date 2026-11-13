# Generic JavaParser Transformation for Package Migration

## Overview
This transformation handles a common breaking change in dependency updates: when a class moves from one package to another.

## Breaking Change Pattern
- **Old API pattern**: `org.apache.thrift.transport.TFramedTransport`
- **New API pattern**: `org.apache.thrift.transport.layered.TFramedTransport`
- **Structural transformation**: Update import statements and type references to use the new package

## Transformation Rule
The transformation performs a simple text replacement:
- Replace all occurrences of `org.apache.thrift.transport.TFramedTransport` with `org.apache.thrift.transport.layered.TFramedTransport`

This handles:
1. Import statements: `import org.apache.thrift.transport.TFramedTransport;`
2. Fully-qualified type references in code
3. Type references in extends/implements clauses
4. Variable declarations, method parameters, and return types

## How to Use
1. Compile the transformation:
   ```bash
   cd /workspace/javaparser-base-template
   mvn compile
   ```

2. Run the transformation on a project:
   ```bash
   java -cp "target/classes:target/dependency/*" github.chains.Main /path/to/project
   ```

## Customization
To adapt this transformation for other package migrations:

1. Update the `OLD_TYPE` and `NEW_TYPE` constants in `Main.java`:
   ```java
   private static final String OLD_TYPE = "old.package.ClassName";
   private static final String NEW_TYPE = "new.package.ClassName";
   ```

2. Recompile and run.

## Limitations
1. Does not handle method signature changes (parameter types, exceptions, new abstract methods)
2. Does not handle constructor signature changes
3. Simple name references rely on import statement updates
4. Wildcard imports (`import old.package.*`) are not transformed

## Example Output
When run on the singer project:
```
Transforming org.apache.thrift.transport.TFramedTransport to org.apache.thrift.transport.layered.TFramedTransport
Processing directory: /workspace/singer
Modified: /workspace/singer/singer/src/test/java/com/pinterest/singer/reader/mapped/TestMappedTBinaryProtocol.java
Modified: /workspace/singer/singer/src/main/java/com/pinterest/singer/utils/SimpleThriftLogger.java
Modified: /workspace/singer/singer/src/main/java/com/pinterest/singer/reader/ThriftReader.java
Transformation completed successfully.
```

## Extending for Other Breaking Changes
For other types of breaking changes (method signatures, new abstract methods, etc.), additional transformation rules would need to be implemented using JavaParser's AST manipulation capabilities.