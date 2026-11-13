package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.code.CtVariableRead;
import java.util.List;

/**
 * Generic Spoon transformation to fix compilation errors caused by
 * logback-classic 1.4.5 requiring org.slf4j.spi.LoggingEventAware interface
 * which doesn't exist in older slf4j-api versions.
 * 
 * Transformation Pattern:
 * Old: ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(...)).setLevel(...)
 * New: Call reflective helper method that avoids compile-time dependency on
 *      ch.qos.logback.classic.Logger
 * 
 * This transformation is generic and reusable for any project affected by
 * this breaking API change between logback versions.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying logback compatibility transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all method calls matching the pattern
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        int transformedCount = 0;
        
        for (CtInvocation<?> invocation : invocations) {
            // Check if this is a setLevel call on a Logger cast expression
            if (isLoggerSetLevelCall(invocation)) {
                System.out.println("Found logback Logger.setLevel() call: " + invocation);
                
                // Get the level argument (e.g., Level.TRACE)
                List<CtExpression<?>> arguments = invocation.getArguments();
                if (arguments.isEmpty()) {
                    System.err.println("Warning: setLevel call has no arguments: " + invocation);
                    continue;
                }
                
                String levelValue = extractLevelValue(arguments.get(0));
                if (levelValue == null) {
                    System.err.println("Warning: Could not extract level value from: " + arguments.get(0));
                    continue;
                }
                
                // Replace with reflective helper method call
                CtLiteral<String> levelLiteral = factory.createLiteral(levelValue);
                CtExecutableReference<?> methodRef = factory.createExecutableReference();
                methodRef.setSimpleName("setRootLogLevelReflectively");
                methodRef.setStatic(true);
                
                CtInvocation<?> newInvocation = factory.Code().createInvocation(
                    null, // static method
                    methodRef,
                    levelLiteral
                );
                
                invocation.replace(newInvocation);
                transformedCount++;
                
                System.out.println("Transformed to: setRootLogLevelReflectively(\"" + levelValue + "\")");
            }
        }
        
        System.out.println("\nTransformed " + transformedCount + " Logger.setLevel() calls.");
        
        if (transformedCount > 0) {
            // Output transformation summary
            System.out.println("\nIMPORTANT: Add the following helper method to your project:");
            System.out.println(generateHelperMethod());
            
            // Note: In a real scenario, we would add this method to a utility class
            // or inject it into the transformed classes
        }
    }
    
    private static boolean isLoggerSetLevelCall(CtInvocation<?> invocation) {
        if (invocation.getExecutable() == null || 
            !invocation.getExecutable().getSimpleName().equals("setLevel")) {
            return false;
        }
        
        // Check if the invocation target involves a cast to Logger
        String invocationStr = invocation.toString();
        return invocationStr.contains("LoggerFactory.getLogger") && 
               invocationStr.contains("Logger)");
    }
    
    private static String extractLevelValue(CtExpression<?> expression) {
        // Extract level from Level.TRACE, Level.DEBUG, etc.
        String exprStr = expression.toString();
        
        // Common log levels
        String[] levels = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "ALL", "OFF"};
        for (String level : levels) {
            if (exprStr.endsWith("." + level) || exprStr.equals(level)) {
                return level;
            }
        }
        
        return null;
    }
    
    private static String generateHelperMethod() {
        return """
            /**
             * Helper method to set logback root logger level reflectively.
             * Required for compatibility with logback 1.4.5 when using older slf4j-api
             * that doesn't have org.slf4j.spi.LoggingEventAware interface.
             */
            public static void setRootLogLevelReflectively(String level) {
                try {
                    Object logger = org.slf4j.LoggerFactory.getLogger("ROOT");
                    Class<?> loggerClass = Class.forName("ch.qos.logback.classic.Logger");
                    Class<?> levelClass = Class.forName("ch.qos.logback.classic.Level");
                    Object levelObj = levelClass.getField(level).get(null);
                    loggerClass.getMethod("setLevel", levelClass).invoke(logger, levelObj);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to set log level reflectively", e);
                }
            }
            """;
    }
}