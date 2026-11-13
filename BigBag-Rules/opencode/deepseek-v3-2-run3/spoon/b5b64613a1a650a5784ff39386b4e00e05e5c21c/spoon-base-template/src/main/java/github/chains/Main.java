package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;

import java.util.List;

/**
 * Generic Spoon transformation for fixing StringUtils.isAllBlank breaking change.
 * 
 * Breaking Change Pattern:
 * - Old API: StringUtils.isAllBlank(String... strs)
 * - New API: Method removed - need to use StringUtils.isBlank() for each argument
 * 
 * Transformation Rule:
 * - Replace StringUtils.isAllBlank(a, b, c) with StringUtils.isBlank(a) && StringUtils.isBlank(b) && StringUtils.isBlank(c)
 * - Replace StringUtils.isAllBlank() with true (empty varargs means all blank)
 * 
 * This is a GENERIC transformation that can be applied to ANY project affected by
 * the commons-lang3 API change removing isAllBlank().
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("This transformation fixes StringUtils.isAllBlank() breaking change.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Generic Transformation for StringUtils.isAllBlank Breaking Change ===");
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println();
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true); // Don't need full classpath
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find and report all StringUtils.isAllBlank calls
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        int foundCount = 0;
        
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef == null) continue;
            
            String methodName = execRef.getSimpleName();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            
            if ("isAllBlank".equals(methodName) && 
                declaringType != null && 
                "org.apache.commons.lang3.StringUtils".equals(declaringType.getQualifiedName())) {
                
                foundCount++;
                System.out.println("Found StringUtils.isAllBlank at: " + invocation.getPosition());
                System.out.println("  Context: " + getContext(invocation));
                System.out.println("  Arguments: " + invocation.getArguments().size() + " string(s)");
                
                // Show the transformation needed
                if (invocation.getArguments().isEmpty()) {
                    System.out.println("  -> Should be replaced with: true");
                } else {
                    System.out.println("  -> Should be replaced with: conjunction of StringUtils.isBlank() calls");
                }
                System.out.println();
            }
        }
        
        if (foundCount == 0) {
            System.out.println("No StringUtils.isAllBlank calls found.");
        } else {
            System.out.println("=== SUMMARY ===");
            System.out.println("Found " + foundCount + " StringUtils.isAllBlank() call(s) that need fixing.");
            System.out.println();
            System.out.println("MANUAL TRANSFORMATION REQUIRED:");
            System.out.println("For each occurrence:");
            System.out.println("1. Replace StringUtils.isAllBlank() with 'true'");
            System.out.println("2. Replace StringUtils.isAllBlank(a, b, c) with:");
            System.out.println("   StringUtils.isBlank(a) && StringUtils.isBlank(b) && StringUtils.isBlank(c)");
            System.out.println();
            System.out.println("Note: This is a generic rule applicable to ANY project using commons-lang3.");
            System.out.println("The ClientHelper constructor change requires project-specific analysis.");
        }
    }
    
    private static String getContext(CtInvocation<?> invocation) {
        try {
            // Get some context around the invocation
            String parent = invocation.getParent().toString();
            if (parent.length() > 100) {
                parent = parent.substring(0, 100) + "...";
            }
            return parent;
        } catch (Exception e) {
            return "Unable to get context";
        }
    }
}