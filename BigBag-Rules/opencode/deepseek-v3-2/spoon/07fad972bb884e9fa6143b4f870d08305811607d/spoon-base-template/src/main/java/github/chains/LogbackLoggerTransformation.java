package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Transformation rule for fixing compilation errors when upgrading to logback-classic 1.4.1
 * with slf4j-api < 2.0.0.
 * 
 * Breaking Change: ch.qos.logback.classic.Logger now implements org.slf4j.spi.LoggingEventAware
 * This interface doesn't exist in slf4j-api < 2.0.0, causing compilation errors.
 * 
 * Transformation: Replace casts to ch.qos.logback.classic.Logger with reflection-based approach
 * that avoids compile-time dependency on LoggingEventAware interface.
 */
public class LogbackLoggerTransformation {
    
    /**
     * Main transformation method.
     * 
     * @param sourceDir Input source directory
     * @param outputDir Output directory for transformed code
     */
    public void transform(String sourceDir, String outputDir) {
        System.out.println("Transforming code to fix logback-classic 1.4.1 compatibility...");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all variable declarations of type ch.qos.logback.classic.Logger
        List<CtLocalVariable> loggerVariables = model.getElements(new TypeFilter<CtLocalVariable>(CtLocalVariable.class) {
            @Override
            public boolean matches(CtLocalVariable variable) {
                CtTypeReference typeRef = variable.getType();
                if (typeRef == null) return false;
                return "ch.qos.logback.classic.Logger".equals(typeRef.getQualifiedName());
            }
        });
        
        System.out.println("Found " + loggerVariables.size() + " variables of type ch.qos.logback.classic.Logger");
        
        for (CtLocalVariable variable : loggerVariables) {
            transformLoggerVariable(variable, factory);
        }
        
        // Apply transformations
        launcher.prettyprint();
        System.out.println("Transformation complete. Output written to: " + outputDir);
    }
    
    /**
     * Transform a single logger variable declaration.
     */
    private void transformLoggerVariable(CtLocalVariable variable, Factory factory) {
        CtElement parent = variable.getParent();
        if (!(parent instanceof CtBlock)) {
            return;
        }
        
        CtBlock block = (CtBlock) parent;
        CtMethod method = variable.getParent(CtMethod.class);
        CtClass clazz = variable.getParent(CtClass.class);
        
        System.out.println("Transforming logger variable in " + 
            method.getSimpleName() + " of " + clazz.getQualifiedName());
        
        // Get the expression being assigned to the variable
        CtExpression expression = variable.getDefaultExpression();
        if (expression == null) {
            return;
        }
        
        String exprString = expression.toString();
        if (!exprString.contains("LoggerFactory.getLogger")) {
            return;
        }
        
        // Create reflection-based code
        // Old: Logger logger = (Logger) LoggerFactory.getLogger(X.class);
        // New: Object logger = LoggerFactory.getLogger(X.class);
        //      try {
        //          Class<?> loggerClass = logger.getClass();
        //          // Call methods via reflection as needed
        //      } catch (Exception e) { ... }
        
        // Change variable type to Object
        CtTypeReference objectType = factory.Type().OBJECT;
        variable.setType(objectType);
        
        System.out.println("  Changed variable type from ch.qos.logback.classic.Logger to Object");
        
        // Note: In a full implementation, we would also need to:
        // 1. Find all method calls on this variable
        // 2. Replace them with reflection calls
        // 3. Add try-catch blocks for reflection exceptions
        // This is a simplified example showing the pattern
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java LogbackLoggerTransformation <source-dir> <output-dir>");
            System.out.println();
            System.out.println("Transformation Rule Summary:");
            System.out.println("===========================");
            System.out.println("Breaking Change: logback-classic 1.4.1 Logger implements LoggingEventAware");
            System.out.println("Problem: Code casting to ch.qos.logback.classic.Logger won't compile with slf4j-api < 2.0.0");
            System.out.println();
            System.out.println("Transformation Pattern:");
            System.out.println("----------------------");
            System.out.println("OLD: Logger logger = (Logger) LoggerFactory.getLogger(X.class);");
            System.out.println("     logger.setLevel(Level.INFO);");
            System.out.println("     logger.addAppender(appender);");
            System.out.println();
            System.out.println("NEW: Object logger = LoggerFactory.getLogger(X.class);");
            System.out.println("     try {");
            System.out.println("         Class<?> loggerClass = logger.getClass();");
            System.out.println("         Method setLevel = loggerClass.getMethod(\"setLevel\", Level.class);");
            System.out.println("         Method addAppender = loggerClass.getMethod(\"addAppender\", Appender.class);");
            System.out.println("         setLevel.invoke(logger, Level.INFO);");
            System.out.println("         addAppender.invoke(logger, appender);");
            System.out.println("     } catch (Exception e) { ... }");
            System.out.println();
            System.out.println("Alternative Fix: Update slf4j-api to >= 2.0.0");
            return;
        }
        
        LogbackLoggerTransformation transformation = new LogbackLoggerTransformation();
        transformation.transform(args[0], args[1]);
    }
}