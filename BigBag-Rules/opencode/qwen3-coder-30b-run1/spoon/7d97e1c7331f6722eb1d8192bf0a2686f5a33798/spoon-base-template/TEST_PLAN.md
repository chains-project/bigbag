# Test to verify the transformation approach

This is a test file to demonstrate the transformation approach works conceptually.

## Java Source Example Before Transformation:
```java
import com.jcabi.aspects.Loggable;

public class TestClass {
    @Loggable(0)
    public void method1() {
        // method body
    }
    
    @Loggable(1)
    public void method2() {
        // method body
    }
    
    @Loggable(2)
    public void method3() {
        // method body
    }
}
```

## Java Source Example After Transformation:
```java
import com.jcabi.aspects.Loggable;

public class TestClass {
    @Loggable(Loggable.DEBUG)
    public void method1() {
        // method body
    }
    
    @Loggable(Loggable.INFO)
    public void method2() {
        // method body
    }
    
    @Loggable(Loggable.WARN)
    public void method3() {
        // method body
    }
}
```

## Transformation Logic:
1. Find all @Loggable annotations in source files
2. For each annotation with integer value:
   - 0 → Loggable.DEBUG
   - 1 → Loggable.INFO
   - 2 → Loggable.WARN
   - 3 → Loggable.ERROR
   - 4 → Loggable.TRACE
3. Replace the integer literal with the enum reference