package github.chains;

import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> <output-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Creating transformation for logback-classic 1.4.5 breaking change...");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        // Create Spoon launcher
        MavenLauncher launcher = new MavenLauncher(sourceDir, MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all type references to ch.qos.logback.classic.Logger
        List<CtTypeReference<?>> loggerRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return element.getQualifiedName() != null && 
                       element.getQualifiedName().equals("ch.qos.logback.classic.Logger");
            }
        });
        
        System.out.println("Found " + loggerRefs.size() + " references to ch.qos.logback.classic.Logger");
        
        // Process each reference - replace with org.slf4j.Logger
        for (CtTypeReference<?> loggerRef : loggerRefs) {
            System.out.println("Processing reference at position: " + loggerRef.getPosition());
            
            // Create reference to org.slf4j.Logger
            CtTypeReference<?> slf4jLoggerRef = loggerRef.getFactory().createReference("org.slf4j.Logger");
            
            // Replace the reference
            try {
                loggerRef.replace(slf4jLoggerRef);
                System.out.println("  Replaced ch.qos.logback.classic.Logger with org.slf4j.Logger");
            } catch (Exception e) {
                System.out.println("  Warning: Could not replace reference: " + e.getMessage());
            }
        }
        
        // Find method calls that might be logback-specific
        // This is a simple approach - look for common logback methods
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                String methodName = element.getExecutable().getSimpleName();
                // Common logback-specific methods
                return methodName.equals("setLevel") || 
                       methodName.equals("addAppender") ||
                       methodName.equals("detachAppender") ||
                       methodName.equals("getLevel");
            }
        });
        
        System.out.println("Found " + invocations.size() + " potentially logback-specific method calls");
        System.out.println("Note: These methods may need manual refactoring after type change");
        
        // Write transformed code
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
        System.out.println("\nBREAKING CHANGE SUMMARY:");
        System.out.println("logback-classic 1.4.5 implements org.slf4j.spi.LoggingEventAware interface");
        System.out.println("This interface doesn't exist in SLF4J 1.x, causing compilation errors");
        System.out.println("\nTRANSFORMATION APPLIED:");
        System.out.println("1. Changed type references from ch.qos.logback.classic.Logger to org.slf4j.Logger");
        System.out.println("\nNEXT STEPS (manual work required):");
        System.out.println("1. For logback-specific method calls (setLevel(), addAppender(), etc.):");
        System.out.println("   - Use reflection: logger.getClass().getMethod(\"setLevel\", Level.class).invoke(logger, level)");
        System.out.println("   - Or rewrite tests to not rely on logback internals");
        System.out.println("   - Or create LogbackTestUtils class with reflection methods");
        System.out.println("2. Consider upgrading SLF4J to 2.x for full compatibility");
        System.out.println("\nGENERIC TRANSFORMATION RULE:");
        System.out.println("This transformation handles the core issue: removing references to");
        System.out.println("ch.qos.logback.classic.Logger type which cannot be loaded when");
        System.out.println("using SLF4J 1.x with logback-classic 1.4.5+");
    }
}