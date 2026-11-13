package github.chains;

import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.CtModel;
import spoon.Launcher;

import java.io.File;
import java.util.List;

/**
 * Generic Spoon transformation to fix GHCompare.Status direct field access issues.
 * This transformation replaces direct access to GHCompare.status field with proper getter methods
 * for compatibility with GitHub API 1.314+ where the field became private.
 */
public class GHCompareStatusFixer {

    public static void fixGHCompareStatusAccess(String sourcePath) {
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        launcher.setSourceOutputDirectory(sourcePath.replace("/src/main/java/", "/target/generated-sources/"));
        launcher.buildModel();

        // Get the model
        CtModel model = launcher.getModel();
        
        // Get factory from launcher
        Factory factory = launcher.getFactory();
        
        // Find all field accesses to GHCompare.status
        List<CtFieldAccess<?>> fieldAccesses = model.getElements(new TypeFilter<>(CtFieldAccess.class));
        
        int fixedCount = 0;
        
        for (CtFieldAccess<?> fieldAccess : fieldAccesses) {
            // Check if this is accessing GHCompare.status field
            if (isGHCompareStatusAccess(fieldAccess)) {
                // Replace with proper getter method call
                if (replaceWithGetterMethod(fieldAccess, factory)) {
                    fixedCount++;
                }
            }
        }
        
        System.out.println("Fixed " + fixedCount + " GHCompare.status access patterns");
    }
    
    private static boolean isGHCompareStatusAccess(CtFieldAccess<?> fieldAccess) {
        CtFieldReference<?> fieldRef = fieldAccess.getVariable();
        
        // Check if field reference is to GHCompare.status
        if ("status".equals(fieldRef.getSimpleName())) {
            // Get the declaring type
            CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                return "org.kohsuke.github.GHCompare".equals(qualifiedName);
            }
        }
        return false;
    }
    
    private static boolean replaceWithGetterMethod(CtFieldAccess<?> fieldAccess, Factory factory) {
        try {
            // Create a method invocation for getStatus()
            CtInvocation<?> newInvocation = factory.createInvocation(
                fieldAccess.getTarget(), // The receiver object
                factory.createExecutableReference()
                    .setSimpleName("getStatus")
            );
            
            // Replace the field access with the method call
            fieldAccess.replace(newInvocation);
            
            return true;
        } catch (Exception e) {
            System.err.println("Failed to replace field access: " + e.getMessage());
            return false;
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java GHCompareStatusFixer <source-path>");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        File sourceDir = new File(sourcePath);
        
        if (!sourceDir.exists()) {
            System.err.println("Source path does not exist: " + sourcePath);
            System.exit(1);
        }
        
        if (sourceDir.isDirectory()) {
            // Process all Java files in the directory recursively
            processDirectory(sourceDir);
        } else {
            // Process single file
            fixGHCompareStatusAccess(sourcePath);
        }
    }
    
    private static void processDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    fixGHCompareStatusAccess(file.getAbsolutePath());
                }
            }
        }
    }
}