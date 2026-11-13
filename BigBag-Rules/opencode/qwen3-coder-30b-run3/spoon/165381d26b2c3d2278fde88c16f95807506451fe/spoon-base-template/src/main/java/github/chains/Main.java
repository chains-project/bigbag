package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation rule to identify JavaParser 3.24.0 breaking changes.
 * This transformation identifies calls to JavaParser.parse() methods that might
 * have changed signatures between versions.
 * 
 * This is a diagnostic tool that locates problematic usage patterns,
 * not a complete automated fix.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "-refactored");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Process the code
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all JavaParser.parse invocations
        List<CtInvocation<?>> parseInvocations = launcher.getModel().getRootPackage()
                .filterChildren(new TypeFilter<>(CtInvocation.class))
                .filterChildren((CtInvocation<?> invocation) -> {
                    CtExecutableReference<?> executableRef = invocation.getExecutable();
                    if (executableRef == null) {
                        return false;
                    }
                    
                    // Look for calls to JavaParser.parse methods
                    return executableRef.getDeclaringType() != null &&
                           executableRef.getDeclaringType().getQualifiedName().contains("JavaParser") &&
                           executableRef.getSimpleName().equals("parse");
                })
                .list();
        
        // Report findings
        System.out.println("Found " + parseInvocations.size() + " JavaParser.parse() invocations");
        
        for (CtInvocation<?> invocation : parseInvocations) {
            // Get the method reference
            CtExecutableReference<?> methodRef = invocation.getExecutable();
            if (methodRef == null) {
                continue;
            }
            
            // Check if it's a JavaParser.parse call
            String declaringTypeName = methodRef.getDeclaringType().getQualifiedName();
            if (declaringTypeName.contains("JavaParser")) {
                // Report the specific call pattern
                System.out.println("JavaParser.parse() call located:");
                System.out.println("  File: " + invocation.getPosition().getFile().getName());
                System.out.println("  Line: " + invocation.getPosition().getLine());
                System.out.println("  Target: " + (invocation.getTarget() != null ? invocation.getTarget().getClass().getSimpleName() : "null"));
                System.out.println("  Arguments: " + invocation.getArguments().size());
                
                // Show more context for important cases
                if (invocation.getTarget() instanceof spoon.reflect.code.CtNewClass) {
                    System.out.println("  Pattern detected: new JavaParser().parse(...) - may require refactoring for JavaParser 3.24.0");
                } else if (invocation.getTarget() != null && 
                          invocation.getTarget().toString().contains("StaticJavaParser")) {
                    System.out.println("  Pattern detected: StaticJavaParser.parse(...) - likely OK");
                }
                System.out.println();
            }
        }
        
        // Write the processed model (but not really modifying)
        launcher.setSourceOutputDirectory(sourceDir + "-refactored");
        launcher.process();
        
        System.out.println("Analysis completed. See above for identified parse() calls.");
        System.out.println("The identified patterns require manual inspection for JavaParser 3.24.0 compatibility.");
    }
}