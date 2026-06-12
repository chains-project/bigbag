package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("The transformation will analyze code for compatibility issues with logback-classic 1.4.1");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Analyzing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Characterize the breaking change:
        // ch.qos.logback.classic.Logger now implements org.slf4j.spi.LoggingEventAware
        // This interface doesn't exist in slf4j-api < 2.x
        // Code casting to ch.qos.logback.classic.Logger will fail to compile
        
        System.out.println("\n=== BREAKING CHANGE ANALYSIS ===");
        System.out.println("Dependency: ch.qos.logback:logback-classic 1.4.1");
        System.out.println("Breaking Change: Logger class now implements org.slf4j.spi.LoggingEventAware");
        System.out.println("Impact: Code casting to ch.qos.logback.classic.Logger fails to compile with slf4j-api < 2.x");
        System.out.println("Required slf4j-api version: >= 2.0.0");
        
        System.out.println("\n=== CODE PATTERN TO TRANSFORM ===");
        System.out.println("Old pattern: Casting LoggerFactory.getLogger() to ch.qos.logback.classic.Logger");
        System.out.println("Example: Logger logger = (Logger) LoggerFactory.getLogger(MyClass.class);");
        System.out.println("\nNew pattern: Use LoggerContext to avoid dependency on LoggingEventAware interface");
        System.out.println("Example: LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();");
        System.out.println("         Logger logger = context.getLogger(MyClass.class);");
        
        // Find all type references to ch.qos.logback.classic.Logger
        List<CtTypeReference> loggerTypeRefs = model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference typeRef) {
                String typeName = typeRef.getQualifiedName();
                return "ch.qos.logback.classic.Logger".equals(typeName);
            }
        });
        
        System.out.println("\n=== FOUND " + loggerTypeRefs.size() + " REFERENCES TO ch.qos.logback.classic.Logger ===");
        
        if (!loggerTypeRefs.isEmpty()) {
            System.out.println("\nFiles containing references to ch.qos.logback.classic.Logger:");
            
            for (CtTypeReference typeRef : loggerTypeRefs) {
                CtType<?> parentType = typeRef.getParent(CtType.class);
                if (parentType != null) {
                    System.out.println("  - " + parentType.getQualifiedName() + ".java");
                    
                    // Get position info
                    System.out.println("    At line: " + typeRef.getPosition().getLine());
                    
                    // Try to get context
                    CtMethod<?> method = typeRef.getParent(CtMethod.class);
                    if (method != null) {
                        System.out.println("    In method: " + method.getSignature());
                    }
                }
            }
            
            System.out.println("\n=== TRANSFORMATION RULE ===");
            System.out.println("For each occurrence of:");
            System.out.println("  Logger logger = (Logger) LoggerFactory.getLogger(ClassName.class);");
            System.out.println("\nReplace with:");
            System.out.println("  LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();");
            System.out.println("  Logger logger = context.getLogger(ClassName.class);");
            System.out.println("\nAdd import if needed:");
            System.out.println("  import ch.qos.logback.classic.LoggerContext;");
            
            System.out.println("\n=== ALTERNATIVE FIX ===");
            System.out.println("Update slf4j-api dependency to >= 2.0.0");
            System.out.println("This is the recommended fix as it maintains type safety.");
        } else {
            System.out.println("\nNo references to ch.qos.logback.classic.Logger found.");
            System.out.println("The project may already be compatible or using a different approach.");
        }
        
        System.out.println("\n=== GENERIC TRANSFORMATION SPECIFICATION ===");
        System.out.println("This transformation rule is applicable to ANY Maven project affected by this breaking change.");
        System.out.println("The rule parameters are:");
        System.out.println("  - Old type: ch.qos.logback.classic.Logger");
        System.out.println("  - New approach: Use ch.qos.logback.classic.LoggerContext");
        System.out.println("  - Method pattern: LoggerFactory.getLogger(*) cast to ch.qos.logback.classic.Logger");
        System.out.println("  - Replacement: LoggerFactory.getILoggerFactory() cast to LoggerContext, then getLogger(*)");
        
        System.out.println("\nAnalysis complete.");
    }
}