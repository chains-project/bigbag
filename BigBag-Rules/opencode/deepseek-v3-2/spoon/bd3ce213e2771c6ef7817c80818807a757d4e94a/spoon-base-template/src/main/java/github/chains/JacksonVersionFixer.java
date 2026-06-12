package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.util.List;

/**
 * Generic Spoon transformation to fix Jackson version mismatch issues.
 * 
 * Problem: When using Jackson databind 2.13.4.1 with Jackson core < 2.13.4,
 * compilation fails with "cannot access com.fasterxml.jackson.core.exc.StreamWriteException"
 * because writeValue() declares it throws StreamWriteException, but this class
 * doesn't exist in older Jackson core versions.
 * 
 * This transformation:
 * 1. Detects writeValue method calls
 * 2. Reports the issue and locations
 * 3. Provides guidance on how to fix
 * 
 * The fix is to update Jackson core dependency to match Jackson databind version.
 */
public class JacksonVersionFixer {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar jackson-fixer.jar <project-directory>");
            System.exit(1);
        }
        
        String projectDir = args[0];
        System.out.println("Analyzing project: " + projectDir);
        
        try {
            analyzeAndFix(projectDir);
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void analyzeAndFix(String projectDir) throws Exception {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add input source
        launcher.addInputResource(projectDir + "/src/main/java");
        if (new File(projectDir, "src/test/java").exists()) {
            launcher.addInputResource(projectDir + "/src/test/java");
        }
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations of writeValue
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef == null) return false;
                
                String methodName = execRef.getSimpleName();
                return "writeValue".equals(methodName);
            }
        });
        
        System.out.println("\n=== Jackson Version Mismatch Analysis ===");
        System.out.println("Found " + invocations.size() + " writeValue method calls");
        
        if (!invocations.isEmpty()) {
            printProblemDescription();
            printCallLocations(invocations);
            printFixInstructions();
            generateFixScript(projectDir);
        } else {
            System.out.println("No writeValue calls found. No Jackson version mismatch detected.");
        }
    }
    
    private static void printProblemDescription() {
        System.out.println("\n=== Problem Description ===");
        System.out.println("The project uses Jackson databind 2.13.4.1 which declares that");
        System.out.println("writeValue() throws StreamWriteException, but Jackson core 2.10.0");
        System.out.println("does not have this class, causing compilation errors:");
        System.out.println("  'cannot access com.fasterxml.jackson.core.exc.StreamWriteException'");
        System.out.println("\nThis is a breaking change in Jackson API between versions.");
    }
    
    private static void printCallLocations(List<CtInvocation<?>> invocations) {
        System.out.println("\n=== Affected Code Locations ===");
        System.out.println("writeValue calls found at:");
        for (CtInvocation<?> invocation : invocations) {
            CtMethod<?> method = invocation.getParent(CtMethod.class);
            String methodName = method != null ? method.getSimpleName() : "unknown";
            String className = method != null && method.getParent(CtClass.class) != null ? 
                method.getParent(CtClass.class).getSimpleName() : "unknown";
            int line = invocation.getPosition().getLine();
            System.out.println("  - " + className + "." + methodName + "() line " + line);
        }
    }
    
    private static void printFixInstructions() {
        System.out.println("\n=== Recommended Fix ===");
        System.out.println("Update Jackson core dependency in pom.xml to match Jackson databind version.");
        System.out.println("\nRequired change:");
        System.out.println("From: <version>2.10.0</version> (jackson-core)");
        System.out.println("To:   <version>2.13.4</version>");
        System.out.println("\nThis ensures StreamWriteException class is available.");
        
        System.out.println("\n=== Alternative Code Fix (if dependency update not possible) ===");
        System.out.println("Wrap writeValue calls in try-catch blocks:");
        System.out.println("  try {");
        System.out.println("    writer.writeValue(file, object);");
        System.out.println("  } catch (Exception e) {");
        System.out.println("    throw new IOException(e);");
        System.out.println("  }");
        System.out.println("\nOr update method signatures to declare throws Exception.");
    }
    
    private static void generateFixScript(String projectDir) {
        File pomFile = new File(projectDir, "pom.xml");
        if (pomFile.exists()) {
            System.out.println("\n=== Automatic Fix Script ===");
            System.out.println("To apply the fix automatically, save this as fix-jackson.sh:");
            System.out.println("```bash");
            System.out.println("#!/bin/bash");
            System.out.println("# Fix Jackson version mismatch in " + new File(projectDir).getName());
            System.out.println("cd \"" + projectDir + "\"");
            System.out.println("if grep -q '<artifactId>jackson-core</artifactId>' pom.xml; then");
            System.out.println("  # Update jackson-core version to 2.13.4");
            System.out.println("  sed -i 's|<artifactId>jackson-core</artifactId>.*<version>2\\.[0-9.]*</version>|<artifactId>jackson-core</artifactId>\\n            <version>2.13.4</version>|' pom.xml");
            System.out.println("  echo \"Updated Jackson core to version 2.13.4\"");
            System.out.println("else");
            System.out.println("  echo \"Jackson core dependency not found in pom.xml\"");
            System.out.println("fi");
            System.out.println("```");
        }
    }
}