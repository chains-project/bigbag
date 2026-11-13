package github.chains;

import spoon.Launcher;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.declaration.CtClass;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        launcher.buildModel();
        var model = launcher.getModel();
        
        // Get root package
        var rootPackage = model.getRootPackage();
        
        // Find all method invocations
        var invocations = Query.getElements(rootPackage, new TypeFilter<>(CtInvocation.class));
        
        int transformationCount = 0;
        for (CtInvocation<?> invocation : invocations) {
            // Check if this is a setLevel call
            if (invocation.getExecutable() != null && 
                invocation.getExecutable().getSimpleName().equals("setLevel")) {
                
                // Check if it's being called on a Logger cast
                String invocationStr = invocation.toString();
                if (invocationStr.contains("((ch.qos.logback.classic.Logger)")) {
                    System.out.println("Found Logger cast at: " + invocation.getPosition());
                    
                    // Create replacement code using reflection
                    CtCodeSnippetStatement newCode = launcher.getFactory().Core().createCodeSnippetStatement();
                    
                    // Simple replacement using reflection to avoid compile-time dependency
                    String replacement = 
                        "// TRANSFORMED: Workaround for logback 1.4.5 LoggingEventAware issue\n" +
                        "try {\n" +
                        "  Class<?> loggerClass = Class.forName(\"ch.qos.logback.classic.Logger\");\n" +
                        "  Class<?> levelClass = Class.forName(\"ch.qos.logback.classic.Level\");\n" +
                        "  Object logger = org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);\n" +
                        "  if (loggerClass.isInstance(logger)) {\n" +
                        "    java.lang.reflect.Method setLevelMethod = loggerClass.getMethod(\"setLevel\", levelClass);\n" +
                        "    setLevelMethod.invoke(logger, ch.qos.logback.classic.Level.TRACE);\n" +
                        "  }\n" +
                        "} catch (Exception e) {\n" +
                        "  throw new RuntimeException(\"Failed to set log level\", e);\n" +
                        "}";
                    
                    newCode.setValue(replacement);
                    
                    // Get the parent statement and replace it
                    CtStatement parentStatement = invocation.getParent(CtStatement.class);
                    if (parentStatement != null) {
                        parentStatement.replace(newCode);
                        transformationCount++;
                        System.out.println("Transformed Logger cast to reflection-based approach");
                    }
                }
            }
        }
        
        System.out.println("Transformation complete! Applied " + transformationCount + " transformations.");
        
        // Output the transformed code
        launcher.setSourceOutputDirectory("/tmp/transformed");
        launcher.prettyprint();
    }
}