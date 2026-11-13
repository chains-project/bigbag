package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Creating Spoon transformation for GHCompare.status field to getStatus() method");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        System.out.println("Building Spoon model...");
        launcher.buildModel();
        
        // Get all field accesses in the model
        List<CtFieldAccess<?>> allFieldAccesses = launcher.getModel()
            .getElements(new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class));
        
        System.out.println("Found " + allFieldAccesses.size() + " field accesses total");
        
        // Filter for .status field accesses
        List<CtFieldAccess<?>> fieldAccesses = new ArrayList<>();
        for (CtFieldAccess<?> fieldAccess : allFieldAccesses) {
            String fieldName = fieldAccess.getVariable().getSimpleName();
            System.out.println("  Field access: " + fieldName + " at " + fieldAccess.getPosition());
            if ("status".equals(fieldName)) {
                fieldAccesses.add(fieldAccess);
            }
        }
        
        System.out.println("Found " + fieldAccesses.size() + " occurrences of .status field access (potential GHCompare.status)");
        
        // Transform each field access to method invocation
        for (CtFieldAccess<?> fieldAccess : fieldAccesses) {
            System.out.println("Transforming field access at: " + fieldAccess.getPosition());
            
            // Get the target expression
            CtExpression<?> target = fieldAccess.getTarget();
            
            // Create an executable reference for the getStatus() method
            CtExecutableReference<?> executableRef = launcher.getFactory().createExecutableReference();
            executableRef.setSimpleName("getStatus");
            
            // Try to preserve the declaring type if available
            CtTypeReference<?> targetType = target.getType();
            if (targetType != null) {
                executableRef.setDeclaringType(targetType);
            }
            
            // Try to preserve the return type
            if (fieldAccess.getType() != null) {
                executableRef.setType(fieldAccess.getType());
            }
            
            // Create the method invocation with empty arguments
            CtInvocation<?> methodInvocation = launcher.getFactory().Code().createInvocation(
                target,
                executableRef,
                new ArrayList<CtExpression<?>>()
            );
            
            // Replace the field access with the method invocation
            fieldAccess.replace(methodInvocation);
        }
        
        // Pretty-print the transformed code
        System.out.println("Writing transformed code to: " + outputDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
}