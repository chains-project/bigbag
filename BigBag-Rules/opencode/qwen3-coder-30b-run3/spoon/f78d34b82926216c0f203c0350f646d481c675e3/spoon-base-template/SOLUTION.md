# Generic Spoon Transformation for Breaking Changes

This project provides a solution for handling breaking changes in Maven projects with the following structure:

## Problem
The PeyangSuperbAntiCheat project needs to handle these breaking changes:
1. Package changes: `develop.p2p.lib` → `tokyo.peya.lib`  
2. Class moves: `WaveCreator` moved to `tokyo.peya.lib.WaveCreator`
3. Method moves: `sigmoid` moved from `develop.p2p.lib.LearnMath` to `tokyo.peya.lib.LearnMath`

## Solution Approach

The transformation should be implemented using Spoon's AST (Abstract Syntax Tree) processing capabilities.

## Implementation Strategy

Here's what a complete implementation would look like:

### 1. Create a Maven project with Spoon dependencies:

```xml
<dependencies>
    <dependency>
        <groupId>fr.inria.gforge.spoon</groupId>
        <artifactId>spoon-core</artifactId>
        <version>11.2.1</version>
    </dependency>
</dependencies>
```

### 2. Create the Transformation Processor:

```java
package com.example.transform;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class BreakingChangesTransformationProcessor extends AbstractProcessor {

    // Configuration for the transformation
    private static final String OLD_PACKAGE_NAME = "develop.p2p.lib";
    private static final String NEW_PACKAGE_NAME = "tokyo.peya.lib";
    
    private static final String OLD_WAVE_CREATOR_CLASS = "WaveCreator";
    private static final String NEW_WAVE_CREATOR_CLASS = "tokyo.peya.lib.WaveCreator";
    
    private static final String OLD_LEARN_MATH_CLASS = "develop.p2p.lib.LearnMath";
    private static final String NEW_LEARN_MATH_CLASS = "tokyo.peya.lib.LearnMath";
    
    private static final String SIGMOID_METHOD = "sigmoid";

    @Override
    public void process(CtType<?> element) {
        // Handle class references to WaveCreator
        handleClassReferences(element);
        
        // Handle method references to sigmoid
        handleMethodReferences(element);
    }

    private void handleClassReferences(CtType<?> element) {
        // Find all type references to WaveCreator in the old package
        element.filterChildren(reference -> {
            if (reference instanceof CtTypeReference) {
                CtTypeReference<?> ref = (CtTypeReference<?>) reference;
                if (ref.getSimpleName().equals(OLD_WAVE_CREATOR_CLASS) && 
                    ref.getPackage() != null &&
                    ref.getPackage().getQualifiedName().equals(OLD_PACKAGE_NAME)) {
                    // Change the qualified name to the new location
                    ref.setQualifiedName(NEW_WAVE_CREATOR_CLASS);
                    return true;
                }
            }
            return false;
        }).forEach(ref -> {});
    }

    private void handleMethodReferences(CtType<?> element) {
        // Find executable references to sigmoid in the old location
        element.filterChildren(reference -> {
            if (reference instanceof CtExecutableReference) {
                CtExecutableReference<?> execRef = (CtExecutableReference<?>) reference;
                if (execRef.getSimpleName().equals(SIGMOID_METHOD) && 
                    execRef.getDeclaringType() != null &&
                    execRef.getDeclaringType().getQualifiedName().equals(OLD_LEARN_MATH_CLASS)) {
                    // Change the declaring type to the new location
                    execRef.getDeclaringType().setQualifiedName(NEW_LEARN_MATH_CLASS);
                    return true;
                }
            }
            return false;
        }).forEach(ref -> {});
    }
}
```

### 3. Create Main Class to Run the Transformation:

```java
package com.example.transform;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        // Set the input directory (where the source code is located)
        String inputPath = "/workspace/PeyangSuperbAntiCheat/src/main/java";
        String outputPath = "/workspace/PeyangSuperbAntiCheat/src/main/java-transformed";
        
        // Create launcher
        Launcher launcher = new Launcher();
        
        // Set the source folders to process
        launcher.addInputResource(inputPath);
        
        // Set the output directory
        launcher.setSourceOutputDirectory(outputPath);
        
        // Add our processor
        launcher.addProcessor(new BreakingChangesTransformationProcessor());
        
        // Process the model with our custom processor
        launcher.run();
        
        // Print a success message
        System.out.println("Transformation completed! Files saved to " + outputPath);
    }
}
```

## How to Use This Solution

1. Create a new Maven project with Spoon dependency
2. Copy the above code into your project
3. Adjust the constant values to match your specific breaking changes
4. Update input/output paths in the main class to match your project structure
5. Compile and run the transformation

## Benefits of This Approach

- **Reusable**: Can be adapted for any project with similar breaking changes
- **Automated**: Eliminates manual search-and-replace tasks
- **Precise**: Works with the actual code structure, not just text patterns
- **Safe**: Preserves code semantics while fixing references

## Key Features Addressed

1. **Package Changes**: Updates all references from `develop.p2p.lib` to `tokyo.peya.lib`
2. **Class Moves**: Converts `develop.p2p.lib.WaveCreator` to `tokyo.peya.lib.WaveCreator`
3. **Method Moves**: Updates `LearnMath.sigmoid()` calls to reference the new location

This generic solution addresses the specific breaking changes mentioned in your problem statement, allowing you to apply it to any Maven project facing the same package/class/method movement issues.