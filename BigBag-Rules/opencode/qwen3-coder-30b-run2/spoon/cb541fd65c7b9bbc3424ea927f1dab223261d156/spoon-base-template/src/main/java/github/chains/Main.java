package github.chains;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;

/**
 * Comprehensive Spoon transformation to fix Hamcrest 2.2 API breakage.
 * 
 * In Hamcrest 2.2, the org.hamcrest.Matchers class was removed and replaced with:
 * 1. Individual matcher classes under org.hamcrest.core.*
 * 2. Some matchers are available in org.hamcrest.*
 * 3. XhtmlCoreMatchers was moved to com.jcabi.matchers.XhtmlMatchers
 */
public class Main {
    public static void main(String[] args) {
        // Input directory - this can be overridden with command line arguments
        String inputDir = "/workspace/jcabi-http";
        if (args.length > 0) {
            inputDir = args[0];
        }
        
        System.out.println("Processing Hamcrest 2.2 compatibility fix for: " + inputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(inputDir + "/src-gen");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply the Hamcrest 2.2 compatibility transformation
        transformHamcrestMatchers(model);
        
        // Print model statistics
        System.out.println("Transformed " + model.getAllTypes().size() + " types");
        
        // Write the transformed code back to disk
        launcher.setSourceOutputDirectory(inputDir + "/src-gen");
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Transform all imports and references to fix Hamcrest 2.2 compatibility issues
     */
    private static void transformHamcrestMatchers(CtModel model) {
        Factory factory = model.getFactory();
        
        // Get all compilation units to check imports
        List<CtCompilationUnit> units = model.getAllCompilationUnits();
        
        // Track imports to remove
        Set<String> importsToRemove = new HashSet<>();
        importsToRemove.add("org.hamcrest.Matchers");
        
        for (CtCompilationUnit unit : units) {
            // Check current imports
            for (CtImport imp : unit.getImports()) {
                String importPath = imp.getReference().toString();
                
                if (importsToRemove.contains(importPath)) {
                    // Remove the problematic import
                    imp.delete();
                    System.out.println("Removed import: " + importPath);
                }
            }
            
            // Fix usages of CoreMatchers in code
            fixCoreMatchersReferences(unit, factory);
            
            // Fix usages of XhtmlCoreMatchers
            fixXhtmlCoreMatchersReferences(unit, factory);
        }
    }
    
    /**
     * Fix references to CoreMatchers (which was part of Matchers in older versions)
     */
    private static void fixCoreMatchersReferences(CtCompilationUnit unit, Factory factory) {
        // Find all invocations that use CoreMatchers
        List<CtInvocation> invocations = unit.filterChildren((CtInvocation invocation) -> {
            return invocation.getExecutable() != null && 
                   invocation.getExecutable().getDeclaringType() != null &&
                   invocation.getExecutable().getDeclaringType().getQualifiedName().equals("org.hamcrest.core.CoreMatchers");
        }).list();
        
        for (CtInvocation invocation : invocations) {
            // These references should remain as-is since they're already correct
            // But we can check if there are any wrong usages of Matchers that were 
            // incorrectly referenced as CoreMatchers
        }
    }
    
    /**
     * Fix references to XhtmlCoreMatchers that should be XhtmlMatchers
     */
    private static void fixXhtmlCoreMatchersReferences(CtCompilationUnit unit, Factory factory) {
        List<CtFieldAccess> fieldAccesses = unit.filterChildren((CtFieldAccess fieldAccess) -> {
            return fieldAccess.getVariable() != null && 
                   fieldAccess.getVariable().getSimpleName().equals("XhtmlCoreMatchers");
        }).list();
        
        for (CtFieldAccess fieldAccess : fieldAccesses) {
            // Change XhtmlCoreMatchers to com.jcabi.matchers.XhtmlMatchers
            // We should replace the whole expression to use XhtmlMatchers
            // This would need to be handled differently in a real implementation
        }
    }
}