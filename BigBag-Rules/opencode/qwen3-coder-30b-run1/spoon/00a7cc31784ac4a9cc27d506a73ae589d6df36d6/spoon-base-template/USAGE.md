# Usage Example

## Running the Transformation

To apply this transformation to a project:

1. **Prepare your project**:
   ```bash
   # Navigate to the transformation directory
   cd /workspace/spoon-base-template
   ```

2. **Run the transformation**:
   ```bash
   # Apply to a specific project directory
   java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -q -DincludeScope=runtime 2>/dev/null; cat classpath.txt) github.chains.Main /path/to/target/project /path/to/output/directory
   ```

3. **Alternative approach using a simple test**:
   ```bash
   # Create a test directory
   mkdir -p test_input
   mkdir -p test_output
   
   # Copy test files
   cp test-input/TestMinaClass.java test_input/
   
   # Run transformation
   java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -q -DincludeScope=runtime 2>/dev/null; cat classpath.txt) github.chains.Main test_input test_output
   ```

## Expected Changes

### Before:
```java
IoUtil.await(sessions);
IoUtil.awaitUninterruptibly(sessions);
IoBuffer buffer = IoBuffer.allocate(1024);
```

### After:
```java
IoUtil.await(sessions, 5L, java.util.concurrent.TimeUnit.SECONDS);
IoUtil.awaitUninterruptibly(sessions, 5L, java.util.concurrent.TimeUnit.SECONDS);
IoBuffer buffer = IoBuffer.allocate(1024, true);
```

## Verification

Check the output directory to verify the transformation was applied correctly:
```bash
ls -la test_output/
cat test_output/TestMinaClass.java
```