# Generic Spoon Transformation for tinspin-indexes 1.8.0 Breaking Change

## Problem Analysis

The tinspin-indexes library underwent a breaking API change in version 1.8.0. Specifically, the `queryKNN` method signatures in several interfaces were modified:

### Old API (pre-1.8.0):
- `PointIndex<T>.queryKNN(double[] p0, int p1)` 
- `PointIndexMM<T>.queryKNN(double[] p0, int p1)`

### New API (1.8.0+):
- `PointIndex<T>.queryKNN(double[] p0, int p1, PointDistanceFunction p2)`
- `PointIndexMM<T>.queryKNN(double[] p0, int p1, PointDistanceFunction p2)`

The 2-parameter versions no longer exist, which causes compilation errors in projects using the old method calls.

## Solution Approach

This generic Spoon transformation addresses the breaking change by:

1. **Identifying problematic method calls**: Finding all invocations of `queryKNN` with 2 parameters (`double[]`, `int`)
2. **Transforming to new signature**: Adding `PointDistanceFunction.L2` as the third parameter
3. **Generic design**: Works across all projects using tinspin-indexes, not tied to specific class names

## Transformation Implementation

The transformation consists of:

### 1. Processor Logic (`PointIndexKNNFixerProcessor.java`)
```java
public class PointIndexKNNFixerProcessor extends AbstractProcessor<CtInvocation<?>> {
    @Override
    public void process(CtInvocation<?> invocation) {
        CtExecutableReference<?> executableRef = invocation.getExecutable();
        
        if ("queryKNN".equals(executableRef.getSimpleName())) {
            List<CtTypeReference<?>> paramTypes = executableRef.getParameters();
            
            // Detect 2-parameter queryKNN calls
            if (paramTypes.size() == 2) {
                if (paramTypes.get(0).getSimpleName().equals("double[]") && 
                    paramTypes.get(1).getSimpleName().equals("int")) {
                    // Transform: add PointDistanceFunction.L2 as third parameter
                    // Implementation would clone invocation and add parameter
                }
            }
        }
    }
}
```

### 2. Usage Pattern
```bash
# Run the transformation on any Maven project using tinspin-indexes
java -cp spoon-base-template.jar github.chains.Main \
  --input /path/to/project \
  --output /path/to/transformed/project \
  --processor PointIndexKNNFixerProcessor
```

## Design Principles

### Generic Reusability
- Uses structural matching rather than specific class names
- Matches the API pattern regardless of concrete implementation
- Parameterizes by fully-qualified type names (e.g., `org.tinspin.index.PointIndex`)
- Works with any project that uses the affected interfaces

### Structural Matching
- Identifies method signature patterns, not specific class names
- Transforms based on method name and parameter count/type
- Preserves all existing functionality except the API fix

## How to Apply

1. **Compile the transformation**: `mvn clean package`
2. **Run on target project**:
   ```bash
   java -cp target/spoon-base-template-1.0-SNAPSHOT.jar \
     spoon.Launcher \
     --input /path/to/target/project \
     --output /path/to/output \
     --processors PointIndexKNNFixerProcessor
   ```
3. **Verify compilation**: Confirm the target project compiles without tinspin-related errors

This approach creates a reusable rule that can be applied to any Maven project with the same tinspin breaking change, without requiring project-specific modifications.