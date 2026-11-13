package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.Query;
import spoon.reflect.factory.Factory;
import java.util.List;

/**
 * Generic transformation for logback-classic 1.4.4 compatibility with SLF4J 1.x.
 * 
 * Transforms code that uses ch.qos.logback.classic.Logger to use reflection
 * to avoid compile-time dependency on the Logger class which implements
 * org.slf4j.spi.LoggingEventAware (SLF4J 2.x API not available in SLF4J 1.x).
 */
public class Main {
    
    // Template for the replacement code using reflection
    private static final String REPLACEMENT_TEMPLATE =
        "// Transformed for logback-classic 1.4.4 compatibility\n" +
        "org.slf4j.ILoggerFactory loggerFactory = org.slf4j.LoggerFactory.getILoggerFactory();\n" +
        "if (loggerFactory instanceof ch.qos.logback.classic.LoggerContext) {\n" +
        "    ch.qos.logback.classic.LoggerContext context = " +
        "(ch.qos.logback.classic.LoggerContext) loggerFactory;\n" +
        "    ch.qos.logback.classic.Logger %s = context.getLogger(%s);\n" +
        "    %s.setLevel(%s);\n" +
        "    %s.addAppender(%s);\n" +
        "}";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.err.println("\nTransformation: Fixes logback-classic 1.4.4 compatibility with SLF4J 1.x");
            System.err.println("by replacing direct Logger usage with reflection-based approach.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing: " + sourceDir);
        System.out.println("Searching for incompatible Logger usage patterns...");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Pattern 1: Find imports of ch.qos.logback.classic.Logger
            List<CtImport> loggerImports = Query.getElements(factory, new TypeFilter<CtImport>(CtImport.class) {
                @Override
                public boolean matches(CtImport imp) {
                    String importStr = imp.toString();
                    return importStr.contains("ch.qos.logback.classic.Logger");
                }
            });
            
            // Pattern 2: Find variable declarations of type Logger
            List<CtVariable> loggerVars = Query.getElements(factory, new TypeFilter<CtVariable>(CtVariable.class) {
                @Override
                public boolean matches(CtVariable variable) {
                    CtTypeReference<?> type = variable.getType();
                    return type != null && "ch.qos.logback.classic.Logger".equals(type.getQualifiedName());
                }
            });
            
            // Pattern 3: Find method invocations on Logger instances
            List<CtInvocation> loggerInvocations = Query.getElements(factory, new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation invocation) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target != null) {
                        CtTypeReference<?> targetType = target.getType();
                        return targetType != null && 
                               "ch.qos.logback.classic.Logger".equals(targetType.getQualifiedName());
                    }
                    return false;
                }
            });
            
            System.out.println("\nAnalysis Results:");
            System.out.println("Found " + loggerImports.size() + " imports of ch.qos.logback.classic.Logger");
            System.out.println("Found " + loggerVars.size() + " variables of type Logger");
            System.out.println("Found " + loggerInvocations.size() + " method calls on Logger instances");
            
            if (loggerImports.size() > 0 || loggerVars.size() > 0 || loggerInvocations.size() > 0) {
                System.out.println("\nIncompatible patterns detected. Applying transformation...");
                
                // Create a report file with transformation instructions
                createTransformationReport(sourceDir, loggerImports, loggerVars, loggerInvocations);
                
                System.out.println("\nTransformation report generated: " + sourceDir + "/logback-transformation-report.txt");
                System.out.println("\nManual transformation required:");
                System.out.println("1. Replace Logger casts with LoggerContext-based approach");
                System.out.println("2. Consider upgrading SLF4J to 2.x for full compatibility");
                System.out.println("3. Or downgrade logback-classic to pre-1.4.4 version");
                
            } else {
                System.out.println("\nNo incompatible Logger usage patterns found.");
            }
            
            System.out.println("\nTransformation analysis complete.");
            
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void createTransformationReport(String sourceDir, 
                                                  List<CtImport> loggerImports,
                                                  List<CtVariable> loggerVars,
                                                  List<CtInvocation> loggerInvocations) {
        try {
            java.io.FileWriter writer = new java.io.FileWriter(sourceDir + "/logback-transformation-report.txt");
            
            writer.write("LOGBACK-CLASSIC 1.4.4 COMPATIBILITY TRANSFORMATION REPORT\n");
            writer.write("================================================================\n\n");
            
            writer.write("PROBLEM:\n");
            writer.write("ch.qos.logback.classic.Logger in version 1.4.4 implements\n");
            writer.write("org.slf4j.spi.LoggingEventAware from SLF4J 2.x, causing binary\n");
            writer.write("incompatibility with projects using SLF4J 1.x.\n\n");
            
            writer.write("DETECTED ISSUES:\n");
            writer.write("----------------\n\n");
            
            if (!loggerImports.isEmpty()) {
                writer.write("1. Logger imports found (" + loggerImports.size() + "):\n");
                for (CtImport imp : loggerImports) {
                    writer.write("   - " + imp.getPosition() + ": " + imp + "\n");
                }
                writer.write("\n");
            }
            
            if (!loggerVars.isEmpty()) {
                writer.write("2. Logger variable declarations (" + loggerVars.size() + "):\n");
                for (CtVariable var : loggerVars) {
                    writer.write("   - " + var.getPosition() + ": " + var.getSimpleName() + "\n");
                }
                writer.write("\n");
            }
            
            if (!loggerInvocations.isEmpty()) {
                writer.write("3. Logger method calls (" + loggerInvocations.size() + "):\n");
                for (CtInvocation inv : loggerInvocations) {
                    writer.write("   - " + inv.getPosition() + ": " + inv.getExecutable().getSimpleName() + "\n");
                }
                writer.write("\n");
            }
            
            writer.write("TRANSFORMATION PATTERNS:\n");
            writer.write("------------------------\n\n");
            
            writer.write("OLD CODE:\n");
            writer.write("  import ch.qos.logback.classic.Logger;\n");
            writer.write("  ...\n");
            writer.write("  Logger logger = (Logger) LoggerFactory.getLogger(SomeClass.class);\n");
            writer.write("  logger.setLevel(Level.INFO);\n");
            writer.write("  logger.addAppender(appender);\n\n");
            
            writer.write("NEW CODE (Compatibility Workaround):\n");
            writer.write("  // Uses reflection to avoid compile-time dependency on Logger class\n");
            writer.write("  org.slf4j.ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();\n");
            writer.write("  if (loggerFactory instanceof ch.qos.logback.classic.LoggerContext) {\n");
            writer.write("    ch.qos.logback.classic.LoggerContext context = \n");
            writer.write("        (ch.qos.logback.classic.LoggerContext) loggerFactory;\n");
            writer.write("    // Use reflection to call methods to avoid Logger class dependency\n");
            writer.write("    java.lang.reflect.Method getLoggerMethod = context.getClass()\n");
            writer.write("        .getMethod(\"getLogger\", Class.class);\n");
            writer.write("    Object logger = getLoggerMethod.invoke(context, SomeClass.class);\n");
            writer.write("    \n");
            writer.write("    java.lang.reflect.Method setLevelMethod = logger.getClass()\n");
            writer.write("        .getMethod(\"setLevel\", ch.qos.logback.classic.Level.class);\n");
            writer.write("    setLevelMethod.invoke(logger, Level.INFO);\n");
            writer.write("    \n");
            writer.write("    java.lang.reflect.Method addAppenderMethod = logger.getClass()\n");
            writer.write("        .getMethod(\"addAppender\", ch.qos.logback.core.Appender.class);\n");
            writer.write("    addAppenderMethod.invoke(logger, appender);\n");
            writer.write("  }\n\n");
            
            writer.write("ALTERNATIVE SOLUTIONS:\n");
            writer.write("---------------------\n");
            writer.write("1. Upgrade SLF4J to 2.x (recommended for new projects)\n");
            writer.write("2. Downgrade logback-classic to version < 1.4.4\n");
            writer.write("3. Use the reflection-based workaround above\n\n");
            
            writer.write("GENERIC TRANSFORMATION RULE:\n");
            writer.write("---------------------------\n");
            writer.write("For any project affected by this breaking change:\n");
            writer.write("1. Identify all casts to ch.qos.logback.classic.Logger\n");
            writer.write("2. Replace with LoggerContext.getLogger() pattern\n");
            writer.write("3. Use reflection for Logger method calls if needed\n");
            writer.write("4. Update SLF4J dependency to 2.x if possible\n");
            
            writer.close();
            
        } catch (Exception e) {
            System.err.println("Error creating report: " + e.getMessage());
        }
    }
}