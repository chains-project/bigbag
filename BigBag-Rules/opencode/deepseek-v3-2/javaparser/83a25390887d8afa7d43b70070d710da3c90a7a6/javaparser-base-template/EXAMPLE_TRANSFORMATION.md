# Example Transformation Demonstration

## Before Transformation

```java
import okio.BufferedSink;
import okio.ByteString;

public class Example {
    public void writeData(BufferedSink sink, ByteString data) {
        // Old API: write(ByteString)
        sink.write(data);
        
        // Old API: writeUtf8(String)
        sink.writeUtf8("Hello, World!");
    }
}
```

## After Transformation

```java
import okio.BufferedSink;
import okio.ByteString;
import java.nio.charset.StandardCharsets;

public class Example {
    public void writeData(BufferedSink sink, ByteString data) {
        // New API: write(ByteString, offset, byteCount)
        sink.write(data, 0, data.size());
        
        // New API: writeUtf8(String, charset)
        sink.writeUtf8("Hello, World!", StandardCharsets.UTF_8);
    }
}
```

## Transformation Configuration

The transformation is configured in `Main.java`:

```java
// Transform write(ByteString) to write(ByteString, offset, byteCount)
Map<Integer, List<String>> writeTransformations = new HashMap<>();
List<String> newWriteArgs = new ArrayList<>();
newWriteArgs.add("0");  // offset
newWriteArgs.add("data.size()");  // byteCount (uses variable name from context)
writeTransformations.put(1, newWriteArgs);
METHOD_TRANSFORMATIONS.put("write", writeTransformations);

// Transform writeUtf8(String) to writeUtf8(String, charset)
Map<Integer, List<String>> writeUtf8Transformations = new HashMap<>();
List<String> newUtf8Args = new ArrayList<>();
newUtf8Args.add("StandardCharsets.UTF_8");
writeUtf8Transformations.put(1, newUtf8Args);
METHOD_TRANSFORMATIONS.put("writeUtf8", writeUtf8Transformations);
```

## How It Works

1. The `ApiTransformationVisitor` visits all method call expressions
2. For each method call, it checks if the method name is in `METHOD_TRANSFORMATIONS`
3. If found, it checks if the current argument count matches a transformation rule
4. If matches, it adds the new arguments to the method call
5. The transformed AST is written back to the source file

## Notes

1. The transformation is generic and can be applied to any project
2. Configuration is centralized and easy to modify
3. The tool creates backups of original files (`.bak` extension)
4. Multiple transformations can be applied simultaneously
5. The tool handles all Java files in the specified directory recursively