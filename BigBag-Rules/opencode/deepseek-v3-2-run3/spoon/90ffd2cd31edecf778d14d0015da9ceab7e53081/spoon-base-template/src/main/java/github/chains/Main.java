package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            launcher.run();
            
            // Get the model
            var model = launcher.getModel();
            
            // Find all classes
            for (CtType<?> type : model.getAllTypes()) {
                if (type instanceof CtClass) {
                    transformClass((CtClass<?>) type);
                }
            }
            
            // Output the transformed code
            launcher.setSourceOutputDirectory(new File(sourceDir));
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully.");
            
        } catch (SpoonClassNotFoundException e) {
            System.err.println("Note: Some classes not found during processing: " + e.getMessage());
            System.out.println("Transformation completed with warnings.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformClass(CtClass<?> clazz) {
        // Check if this class uses ch.qos.logback.classic.Logger
        boolean hasLoggerUsage = false;
        
        // Look for imports of ch.qos.logback.classic.Logger
        for (CtImport imp : clazz.getImports()) {
            if (imp.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) imp.getReference();
                if ("ch.qos.logback.classic.Logger".equals(typeRef.getQualifiedName())) {
                    hasLoggerUsage = true;
                    break;
                }
            }
        }
        
        if (!hasLoggerUsage) {
            return;
        }
        
        System.out.println("Transforming class: " + clazz.getQualifiedName());
        
        // Find all variable declarations
        List<CtVariable<?>> variables = Query.getElements(clazz, new TypeFilter<CtVariable<?>>(CtVariable.class) {
            @Override
            public boolean matches(CtVariable<?> variable) {
                return true;
            }
        });
        
        for (CtVariable<?> var : variables) {
            transformVariable(var);
        }
        
        // Also transform any casts to Logger
        List<CtCast<?>> casts = Query.getElements(clazz, new TypeFilter<CtCast<?>>(CtCast.class) {
            @Override
            public boolean matches(CtCast<?> cast) {
                return true;
            }
        });
        
        for (CtCast<?> cast : casts) {
            transformCast(cast);
        }
    }
    
    private static void transformVariable(CtVariable<?> var) {
        // Check if variable type is ch.qos.logback.classic.Logger
        if (var.getType() != null && "ch.qos.logback.classic.Logger".equals(var.getType().getQualifiedName())) {
            System.out.println("  Transforming variable: " + var.getSimpleName());
            
            // Change type to org.slf4j.Logger
            CtTypeReference<?> newType = var.getFactory().Type().createReference("org.slf4j.Logger");
            var.setType(newType);
            
            // If default expression is a cast to Logger, we need to handle it
            if (var.getDefaultExpression() instanceof CtCast) {
                CtCast<?> cast = (CtCast<?>) var.getDefaultExpression();
                if (cast.getType() != null && "ch.qos.logback.classic.Logger".equals(cast.getType().getQualifiedName())) {
                    // Remove the cast - just use the expression
                    var.setDefaultExpression(cast.getExpression());
                }
            }
        }
    }
    
    private static void transformCast(CtCast<?> cast) {
        // Check if cast is to ch.qos.logback.classic.Logger
        if (cast.getType() != null && "ch.qos.logback.classic.Logger".equals(cast.getType().getQualifiedName())) {
            System.out.println("  Transforming cast to Logger");
            
            // Check if this is a cast of LoggerFactory.getLogger()
            CtExpression<?> expr = cast.getExpression();
            if (expr instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) expr;
                if (invocation.getExecutable() != null && 
                    invocation.getExecutable().getSignature() != null &&
                    invocation.getExecutable().getSignature().contains("LoggerFactory.getLogger")) {
                    
                    // Replace with a call to a compatibility method
                    // We'll create a method call to a helper
                    CtTypeReference<?> helperType = cast.getFactory().Type().createReference("org.slf4j.LoggerFactory");
                    CtExecutableReference<?> getLoggerMethod = cast.getFactory().Executable().createReference(
                        helperType,
                        cast.getFactory().Type().createReference("org.slf4j.Logger"),
                        "getLogger",
                        cast.getFactory().Type().createReference("java.lang.Class")
                    );
                    
                    // Keep the same arguments
                    List<CtExpression<?>> args = invocation.getArguments();
                    
                    // Create the new invocation
                    CtInvocation<?> newInvocation = cast.getFactory().createInvocation(
                        null,
                        getLoggerMethod,
                        args.toArray(new CtExpression[0])
                    );
                    
                    // Replace the cast with the invocation
                    cast.replace(newInvocation);
                }
            }
        }
    }
}