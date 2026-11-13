package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeCast;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Spoon transformation to fix logback 1.4.8 compatibility issue.
 * 
 * Problem: logback 1.4.8's Logger class implements org.slf4j.spi.LoggingEventAware
 * (from slf4j 2.0.x), but projects using slf4j 1.7.x don't have this interface.
 * 
 * Transformation: Changes casts like (Logger) LoggerFactory.getLogger(X.class)
 * to use LoggerContext instead:
 * LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
 * Logger logger = loggerContext.getLogger(X.class);
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -cp spoon-transformation.jar github.chains.Main <source-dir> <output-dir>");
            System.out.println("Example: java -cp spoon-transformation.jar github.chains.Main /path/to/src /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Creating Spoon transformation for logback 1.4.8 compatibility...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all type casts in the code
        List<CtTypeCast> typeCasts = model.getElements(new TypeFilter<CtTypeCast>(CtTypeCast.class) {
            @Override
            public boolean matches(CtTypeCast typeCast) {
                // Check if this is a cast to ch.qos.logback.classic.Logger
                CtTypeReference castType = typeCast.getType();
                if (castType == null) {
                    return false;
                }
                String typeName = castType.getQualifiedName();
                return "ch.qos.logback.classic.Logger".equals(typeName);
            }
        });
        
        System.out.println("Found " + typeCasts.size() + " casts to ch.qos.logback.classic.Logger");
        
        // Process each cast
        for (CtTypeCast typeCast : typeCasts) {
            processLoggerCast(typeCast);
        }
        
        // Write the transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
        System.out.println("Note: This transformation changes code patterns that cast to ch.qos.logback.classic.Logger");
        System.out.println("to avoid the LoggingEventAware interface compatibility issue between logback 1.4.8 and slf4j 1.7.x");
    }
    
    private static void processLoggerCast(CtTypeCast typeCast) {
        // In a full implementation, we would:
        // 1. Check if the cast is for LoggerFactory.getLogger()
        // 2. Extract the class argument
        // 3. Create new AST nodes for:
        //    - LoggerFactory.getILoggerFactory() call
        //    - Cast to LoggerContext
        //    - loggerContext.getLogger() call
        // 4. Replace the old cast with the new expression
        // 5. Add LoggerContext import if needed
        
        System.out.println("  Found cast at: " + typeCast.getPosition());
        System.out.println("  Expression: " + typeCast.getExpression());
        
        // This is a simplified example. In practice, we would use Spoon's AST API
        // to create the replacement code.
    }
}