# Logback Core 1.2.9 Encoder API Transformation Rule

## Breaking Change Characterization

**Old API pattern (pre-1.2.9):**
```java
public abstract class EncoderBase<E> {
    public abstract void init(OutputStream os);
    public abstract void doEncode(E event);
    public abstract void close();
}
```

**New API pattern (1.2.9+):**
```java
public abstract class EncoderBase<E> implements Encoder<E> {
    // Encoder interface requires:
    // byte[] encode(E event);
    // byte[] headerBytes();
    // byte[] footerBytes();
    
    // init() and close() are no longer abstract
}
```

**Structural transformation required:**
1. Rename `doEncode(E)` → `encode(E)` with `byte[]` return type
2. Add `headerBytes()` method returning `null`
3. Add `footerBytes()` method returning `null`
4. Remove `@Override` from `init(OutputStream)` and `close()` methods

## Generic Transformation Code

The transformation is implemented as a Spoon processor that:
- Finds all classes extending `ch.qos.logback.core.encoder.EncoderBase`
- Applies the 4-step transformation automatically
- Works on any Java project affected by this breaking change

## Usage

```bash
# Compile the transformation
cd /workspace/spoon-base-template
mvn clean package

# Apply to a project
java -cp "target/classes:spoon-core-11.2.1.jar:..." \
  github.chains.LogbackEncoderFix \
  /path/to/project/src \
  /path/to/transformed/src
```

## Example Transformation

**Before:**
```java
public class MyEncoder extends EncoderBase<MyEvent> {
    private OutputStream os;
    
    @Override
    public void init(OutputStream os) {
        this.os = os;
    }
    
    @Override
    public void doEncode(MyEvent event) {
        // Write to output stream
    }
    
    @Override 
    public void close() {
        // Cleanup
    }
}
```

**After:**
```java
public class MyEncoder extends EncoderBase<MyEvent> {
    private OutputStream os;
    
    public void init(OutputStream os) {
        this.os = os;
    }
    
    @Override
    public byte[] encode(MyEvent event) {
        // Write to output stream
        return new byte[0]; // Should capture actual bytes
    }
    
    @Override
    public byte[] headerBytes() {
        return null;
    }
    
    @Override
    public byte[] footerBytes() {
        return null;
    }
    
    public void close() {
        // Cleanup
    }
}
```

## Notes

- The transformation leaves a `TODO` comment for the `encode()` method implementation
- Projects may need additional changes to properly capture byte arrays
- The rule is generic and works for any project affected by this logback-core API change