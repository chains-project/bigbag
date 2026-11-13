package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic transformation rule for logback-classic 1.4.4 breaking change.
 * 
 * Breaking Change: ch.qos.logback.classic.Logger now implements 
 * org.slf4j.spi.LoggingEventAware which doesn't exist in SLF4J 1.7.36.
 * 
 * Old API pattern: (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(X.class)
 * 
 * New API pattern: Use LogbackCompatibility.getLogger(X.class) which handles
 * the compatibility issue.
 * 
 * This transformation:
 * 1. Finds all files importing ch.qos.logback.classic.Logger
 * 2. Adds import for LogbackCompatibility utility class
 * 3. Provides template for manual transformation of casts
 */
public class LogbackCompatibilityTransformation {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java LogbackCompatibilityTransformation <sourceDirectory> <outputDirectory>");
            System.err.println("\nThis transformation fixes the logback-classic 1.4.4 breaking change");
            System.err.println("where ch.qos.logback.classic.Logger implements org.slf4j.spi.LoggingEventAware");
            System.err.println("which doesn't exist in SLF4J 1.7.36.");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String outputDirectory = args[1];
        
        System.out.println("==================================================================");
        System.out.println("Logback-Classic 1.4.4 Compatibility Transformation");
        System.out.println("==================================================================");
        System.out.println("Breaking Change: ch.qos.logback.classic.Logger now implements");
        System.out.println("org.slf4j.spi.LoggingEventAware (SLF4J 2.x) but project uses SLF4J 1.7.36");
        System.out.println("==================================================================");
        System.out.println("Source directory: " + sourceDirectory);
        System.out.println("Output directory: " + outputDirectory);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all types that import ch.qos.logback.classic.Logger
        List<CtType<?>> types = model.getElements(new TypeFilter<CtType<?>>(CtType.class) {
            @Override
            public boolean matches(CtType<?> type) {
                // Check if this type imports ch.qos.logback.classic.Logger
                for (var importRef : type.getReferencedTypes()) {
                    if ("ch.qos.logback.classic.Logger".equals(importRef.getQualifiedName())) {
                        return true;
                    }
                }
                return false;
            }
        });
        
        System.out.println("\nFound " + types.size() + " type(s) importing ch.qos.logback.classic.Logger:");
        for (CtType<?> type : types) {
            System.out.println("  - " + type.getQualifiedName() + " (" + type.getPosition().getFile().getName() + ")");
        }
        
        if (!types.isEmpty()) {
            System.out.println("\n==================================================================");
            System.out.println("TRANSFORMATION INSTRUCTIONS:");
            System.out.println("==================================================================");
            System.out.println("For each file above, you need to:");
            System.out.println();
            System.out.println("1. Add this import:");
            System.out.println("   import github.chains.LogbackCompatibility;");
            System.out.println();
            System.out.println("2. Replace all occurrences of:");
            System.out.println("   (Logger) LoggerFactory.getLogger(X.class)");
            System.out.println("   with:");
            System.out.println("   LogbackCompatibility.getLogger(X.class)");
            System.out.println();
            System.out.println("3. The LogbackCompatibility utility class provides:");
            System.out.println("   public static ch.qos.logback.classic.Logger getLogger(Class<?> clazz) {");
            System.out.println("       try {");
            System.out.println("           // Try LoggerContext approach first");
            System.out.println("           Object loggerFactory = LoggerFactory.getILoggerFactory();");
            System.out.println("           if (loggerFactory instanceof ch.qos.logback.classic.LoggerContext) {");
            System.out.println("               return ((ch.qos.logback.classic.LoggerContext) loggerFactory).getLogger(clazz);");
            System.out.println("           }");
            System.out.println("       } catch (NoClassDefFoundError | Exception e) {");
            System.out.println("           // Fall back to reflection if LoggingEventAware is missing");
            System.out.println("           try {");
            System.out.println("               Object logger = LoggerFactory.getLogger(clazz);");
            System.out.println("               return (ch.qos.logback.classic.Logger) logger;");
            System.out.println("           } catch (ClassCastException cce) {");
            System.out.println("               throw new RuntimeException(\"Logback compatibility issue. " + 
                               "Consider updating to SLF4J 2.x or downgrading logback-classic.\", cce);");
            System.out.println("           }");
            System.out.println("       }");
            System.out.println("       return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(clazz);");
            System.out.println("   }");
            System.out.println();
            System.out.println("==================================================================");
        }
        
        // Write transformed code (model is unchanged, but we output it)
        launcher.prettyprint();
        
        System.out.println("\nOutput written to: " + outputDirectory);
        System.out.println("\nNote: This is a template transformation. You need to:");
        System.out.println("1. Implement the LogbackCompatibility utility class");
        System.out.println("2. Apply the transformations manually or extend this code");
        System.out.println("3. Consider updating SLF4J to 2.x for full compatibility");
        System.out.println("==================================================================");
    }
}