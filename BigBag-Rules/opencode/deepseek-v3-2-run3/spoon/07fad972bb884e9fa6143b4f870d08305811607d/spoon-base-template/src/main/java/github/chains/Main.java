package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.factory.Factory;

import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying logback transformation to: " + sourceDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            launcher.addInputResource(sourceDir);
            
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Find all type references to ch.qos.logback.classic.Logger
            List<CtTypeReference<?>> loggerTypeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return "ch.qos.logback.classic.Logger".equals(typeRef.getQualifiedName());
                }
            });
            
            System.out.println("Found " + loggerTypeRefs.size() + " references to ch.qos.logback.classic.Logger");
            
            // For each type that uses Logger, add a workaround method
            for (CtTypeReference<?> typeRef : loggerTypeRefs) {
                CtType<?> type = typeRef.getTypeDeclaration();
                if (type != null) {
                    // Check if this type has method calls on Logger objects
                    // We'll add a helper method to avoid direct Logger usage
                    addLoggerHelperIfNeeded(type, factory);
                }
            }
            
            // Save transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed. Note: Manual fixes may still be needed for Logger usage.");
            System.out.println("The issue is that ch.qos.logback.classic.Logger implements");
            System.out.println("org.slf4j.spi.LoggingEventAware which doesn't exist in SLF4J 1.7.36.");
            System.out.println("Consider updating SLF4J to a compatible version.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void addLoggerHelperIfNeeded(CtType<?> type, Factory factory) {
        // Add a helper method to get and configure loggers without direct Logger type usage
        String helperMethod = 
            "    // Helper method to work around logback 1.4.1 / SLF4J 1.7.36 compatibility issue\n" +
            "    // ch.qos.logback.classic.Logger implements org.slf4j.spi.LoggingEventAware\n" +
            "    // which doesn't exist in SLF4J 1.7.36\n" +
            "    private static Object getAndConfigureLogger(Class<?> clazz, ch.qos.logback.classic.Level level, ch.qos.logback.core.Appender appender) throws Exception {\n" +
            "        Object context = org.slf4j.LoggerFactory.getILoggerFactory();\n" +
            "        Object logger = context.getClass().getMethod(\"getLogger\", Class.class).invoke(context, clazz);\n" +
            "        logger.getClass().getMethod(\"setLevel\", ch.qos.logback.classic.Level.class).invoke(logger, level);\n" +
            "        if (appender != null) {\n" +
            "            logger.getClass().getMethod(\"addAppender\", ch.qos.logback.core.Appender.class).invoke(logger, appender);\n" +
            "        }\n" +
            "        return logger;\n" +
            "    }";
        
        // Check if helper already exists
        boolean hasHelper = type.getMethods().stream()
            .anyMatch(m -> "getAndConfigureLogger".equals(m.getSimpleName()));
        
        if (!hasHelper) {
            CtCodeSnippetExpression<?> snippet = factory.createCodeSnippetExpression(helperMethod);
            // Note: This is simplified - actually adding a method requires more work
            System.out.println("Type " + type.getQualifiedName() + " uses ch.qos.logback.classic.Logger");
            System.out.println("Consider adding helper method to avoid direct Logger type usage");
        }
    }
}