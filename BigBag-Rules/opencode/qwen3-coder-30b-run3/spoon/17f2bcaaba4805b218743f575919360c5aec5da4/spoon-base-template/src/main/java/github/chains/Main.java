package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Initialize Spoon
        Launcher launcher = new Launcher();
        launcher.addInputResource("./PGS/src/main/java");
        launcher.setSourceOutputDirectory("./transformed");

        // Build the model
        CtModel model = launcher.buildModel();

        // Create a visitor to transform the code
        model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(CtInvocation.class)).forEach(invocation -> {
            transformQuery1NNCall(invocation);
        });

        // Save the transformed model
        launcher.setSourceOutputDirectory("./transformed");
        launcher.process();
        
        // Print the transformed model to the console
        System.out.println("Transformation complete.");
        System.out.println("Transformed files are in ./transformed directory");
    }

    private static void transformQuery1NNCall(CtInvocation invocation) {
        // Look for methods with the name "query1NN"
        if (invocation.getExecutable().getSimpleName().equals("query1NN")) {
            // Check if this method call is part of a chained call (i.e. .dist() or .value())
            CtExpression parent = invocation.getParent();
            
            // If we're looking at a chained call like query1NN(...).dist() or query1NN(...).value()
            if (parent instanceof CtInvocation) {
                CtInvocation chainedCall = (CtInvocation) parent;
                String methodName = chainedCall.getExecutable().getSimpleName();
                
                // Special case handling for .dist() and .value() calls
                if ("dist".equals(methodName) || "value".equals(methodName)) {
                    // Replace with appropriate getter method
                    System.out.println("Found query1NN call with " + methodName + "() call: " + invocation.toString());
                    
                    // Replace the chained call with the correct method
                    // For dist(): PointEntryDist<T>.dist() becomes PointEntryDist<T>.getDist()
                    // For value(): PointEntryDist<T>.value() becomes PointEntryDist<T>.getValue()
                    if ("dist".equals(methodName)) {
                        chainedCall.getExecutable().setSimpleName("getDist");
                    } else if ("value".equals(methodName)) {
                        chainedCall.getExecutable().setSimpleName("getValue");
                    }
                }
            }
        }
    }
}