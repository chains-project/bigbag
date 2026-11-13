package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;

import java.util.List;

/**
 * Generic Spoon transformation to fix the logback-classic 1.4.4 breaking change.
 * 
 * Breaking change: ch.qos.logback.classic.Logger now implements 
 * org.slf4j.spi.LoggingEventAware, which doesn't exist in older slf4j-api versions.
 * 
 * Old pattern: ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.TRACE)
 * New pattern: Use reflection to avoid referencing Logger class
 * 
 * This transformation is generic and can be applied to any project affected by this breaking change.
 */
public class Main {
    
    // Configuration: fully qualified class names
    private static final String LOGGER_CLASS = "ch.qos.logback.classic.Logger";
    private static final String LOGGER_FACTORY_CLASS = "org.slf4j.LoggerFactory";
    private static final String GET_LOGGER_METHOD = "getLogger";
    private static final String ROOT_LOGGER_NAME_FIELD = "ROOT_LOGGER_NAME";
    private static final String SET_LEVEL_METHOD = "setLevel";
    private static final String LEVEL_CLASS = "ch.qos.logback.classic.Level";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation for logback-classic 1.4.4 breaking change");
        System.out.println("Source directory: " + sourceDir);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            
            // Use noClasspath to avoid dependency issues during transformation
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(sourceDir);
            
            // Build the model
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            System.out.println("Model built successfully");
            
            // Find and fix all problematic setLevel calls
            int fixedCalls = fixSetLevelCalls(factory);
            System.out.println("Fixed " + fixedCalls + " setLevel calls");
            
            // Find and replace Logger.ROOT_LOGGER_NAME
            int fixedFieldRefs = fixRootLoggerNameReferences(factory);
            System.out.println("Fixed " + fixedFieldRefs + " Logger.ROOT_LOGGER_NAME references");
            
            // Write transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully");
            System.out.println("Summary:");
            System.out.println("  - Fixed setLevel calls: " + fixedCalls);
            System.out.println("  - Fixed ROOT_LOGGER_NAME references: " + fixedFieldRefs);
            System.out.println("");
            System.out.println("Note: The transformation uses reflection to avoid");
            System.out.println("referencing ch.qos.logback.classic.Logger class,");
            System.out.println("which now implements org.slf4j.spi.LoggingEventAware");
            System.out.println("(not available in older slf4j-api versions).");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Find and fix all problematic setLevel calls with Logger casts.
     */
    private static int fixSetLevelCalls(Factory factory) {
        List<CtInvocation<?>> invocations = Query.getElements(factory, new Filter<CtInvocation<?>>() {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                // Check if this is a setLevel call
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef == null || !SET_LEVEL_METHOD.equals(execRef.getSimpleName())) {
                    return false;
                }
                
                // Check if target expression contains a cast to Logger
                CtExpression<?> target = invocation.getTarget();
                if (target == null) {
                    return false;
                }
                
                String targetStr = target.toString();
                return targetStr.contains("(" + LOGGER_CLASS + ")") || 
                       targetStr.contains("(Logger)");
            }
        });
        
        int count = 0;
        for (CtInvocation<?> invocation : invocations) {
            if (transformSetLevelCall(invocation)) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Transform a single setLevel call to use reflection.
     */
    private static boolean transformSetLevelCall(CtInvocation<?> invocation) {
        try {
            // Get the level argument
            String levelArg = "ch.qos.logback.classic.Level.TRACE"; // default
            List<CtExpression<?>> args = invocation.getArguments();
            if (!args.isEmpty()) {
                levelArg = args.get(0).toString();
            }
            
            // Create reflection-based code
            String reflectionCode = createReflectionCode("\"ROOT\"", levelArg);
            
            // Replace the entire statement
            CtStatement statement = invocation.getParent(CtStatement.class);
            if (statement != null) {
                CtCodeSnippetStatement replacement = statement.getFactory().createCodeSnippetStatement(reflectionCode);
                statement.replace(replacement);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to transform setLevel call at line " + 
                             invocation.getPosition().getLine() + ": " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Find and replace Logger.ROOT_LOGGER_NAME with "ROOT".
     */
    private static int fixRootLoggerNameReferences(Factory factory) {
        List<CtFieldRead> fieldReads = Query.getElements(factory, new Filter<CtFieldRead>() {
            @Override
            public boolean matches(CtFieldRead fieldRead) {
                // Check if this is Logger.ROOT_LOGGER_NAME
                String fieldReadStr = fieldRead.toString();
                return fieldReadStr.contains("Logger." + ROOT_LOGGER_NAME_FIELD) ||
                       fieldReadStr.contains(LOGGER_CLASS + "." + ROOT_LOGGER_NAME_FIELD);
            }
        });
        
        int count = 0;
        for (CtFieldRead fieldRead : fieldReads) {
            try {
                CtLiteral<String> replacement = fieldRead.getFactory().createLiteral("\"ROOT\"");
                fieldRead.replace(replacement);
                count++;
            } catch (Exception e) {
                System.err.println("Failed to replace ROOT_LOGGER_NAME at line " + 
                                 fieldRead.getPosition().getLine() + ": " + e.getMessage());
            }
        }
        
        return count;
    }
    
    /**
     * Create reflection code to set log level without referencing Logger class.
     */
    private static String createReflectionCode(String loggerName, String level) {
        return "try {\n" +
               "    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(" + loggerName + ");\n" +
               "    java.lang.reflect.Method setLevelMethod = logger.getClass().getMethod(\"setLevel\", " +
               "java.lang.Class.forName(\"" + LEVEL_CLASS + "\"));\n" +
               "    setLevelMethod.invoke(logger, " + level + ");\n" +
               "} catch (java.lang.ClassNotFoundException | java.lang.NoSuchMethodException | " +
               "java.lang.IllegalAccessException | java.lang.reflect.InvocationTargetException e) {\n" +
               "    // Ignore - logging configuration failed\n" +
               "}";
    }
}