package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.factory.Factory;

import java.util.*;

/**
 * Generic transformation to fix logback-classic 1.4.6 binary incompatibility with SLF4J 1.7.x.
 * 
 * This transformation addresses the breaking change where ch.qos.logback.classic.Logger
 * in version 1.4.6 implements org.slf4j.spi.LoggingEventAware, which doesn't exist in SLF4J 1.7.x.
 * 
 * The transformation is generic and can be applied to any Java project by specifying
 * the source directory as the first argument.
 */
public class Main {
    
    // Configuration
    private static final String PROBLEMATIC_TYPE = "ch.qos.logback.classic.Logger";
    private static final String REPLACEMENT_TYPE = "org.slf4j.Logger";
    
    // Track statistics
    private static int fixesApplied = 0;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-transformation.jar github.chains.Main <source-directory>");
            System.err.println("\nThis transformation fixes the logback 1.4.6 / SLF4J 1.7.x compatibility issue.");
            System.err.println("It transforms code that references " + PROBLEMATIC_TYPE + " to use " + REPLACEMENT_TYPE + " instead.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying logback compatibility fix to: " + sourceDir);
        
        try {
            // Initialize Spoon
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setLevel("OFF"); // Reduce verbose output
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Apply fixes
            applyFixes(model);
            
            System.out.println("\nApplied " + fixesApplied + " fixes");
            
            // Write output if changes were made
            if (fixesApplied > 0) {
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("Transformed code written to: " + sourceDir);
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void applyFixes(CtModel model) {
        for (CtType<?> type : model.getAllTypes()) {
            processType(type);
        }
    }
    
    private static void processType(CtType<?> type) {
        Factory factory = type.getFactory();
        
        // Fix type declarations
        fixTypeDeclarations(type, factory);
        
        // Fix method calls on problematic types
        fixMethodCalls(type, factory);
    }
    
    private static void fixTypeDeclarations(CtType<?> type, Factory factory) {
        // Fix local variables
        type.getElements(new TypeFilter<CtLocalVariable<?>>(CtLocalVariable.class) {
            @Override
            public boolean matches(CtLocalVariable<?> var) {
                return isProblematicType(var.getType());
            }
        }).forEach(var -> {
            fixVariableType(var, factory);
            fixesApplied++;
        });
        
        // Fix fields
        type.getElements(new TypeFilter<CtField<?>>(CtField.class) {
            @Override
            public boolean matches(CtField<?> field) {
                return isProblematicType(field.getType());
            }
        }).forEach(field -> {
            field.setType(factory.createReference(REPLACEMENT_TYPE));
            fixesApplied++;
        });
        
        // Fix method parameters
        type.getElements(new TypeFilter<CtMethod<?>>(CtMethod.class)).forEach(method -> {
            for (CtParameter<?> param : method.getParameters()) {
                if (isProblematicType(param.getType())) {
                    param.setType(factory.createReference(REPLACEMENT_TYPE));
                    fixesApplied++;
                }
            }
        });
        
        // Fix constructor parameters
        type.getElements(new TypeFilter<CtConstructor<?>>(CtConstructor.class)).forEach(constructor -> {
            for (CtParameter<?> param : constructor.getParameters()) {
                if (isProblematicType(param.getType())) {
                    param.setType(factory.createReference(REPLACEMENT_TYPE));
                    fixesApplied++;
                }
            }
        });
    }
    
    private static void fixVariableType(CtLocalVariable<?> var, Factory factory) {
        // Change the type
        var.setType(factory.createReference(REPLACEMENT_TYPE));
        
        // Add a comment about the change
        String comment = "// Type changed from " + PROBLEMATIC_TYPE + " to " + REPLACEMENT_TYPE + 
                        " for logback 1.4.6 compatibility";
        var.addComment(factory.createComment(comment, CtComment.CommentType.INLINE));
    }
    
    private static void fixMethodCalls(CtType<?> type, Factory factory) {
        // Find method calls on problematic types
        List<CtInvocation<?>> problematicInvocations = new ArrayList<>();
        
        type.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExpression<?> target = invocation.getTarget();
                if (target == null) return false;
                
                if (isProblematicType(target.getType())) {
                    String methodName = invocation.getExecutable().getSimpleName();
                    // Check for logback-specific methods
                    return isLogbackSpecificMethod(methodName);
                }
                return false;
            }
        }).forEach(problematicInvocations::add);
        
        // Process each problematic invocation
        for (CtInvocation<?> invocation : problematicInvocations) {
            String methodName = invocation.getExecutable().getSimpleName();
            
            // Add comment explaining the issue
            String comment = "// FIXME: " + methodName + "() is a logback-specific method that may not work with " + REPLACEMENT_TYPE;
            CtStatement parent = invocation.getParent(CtStatement.class);
            if (parent != null) {
                parent.addComment(factory.createComment(comment, CtComment.CommentType.INLINE));
                fixesApplied++;
            }
        }
    }
    
    private static boolean isProblematicType(CtTypeReference<?> typeRef) {
        return typeRef != null && PROBLEMATIC_TYPE.equals(typeRef.getQualifiedName());
    }
    
    private static boolean isLogbackSpecificMethod(String methodName) {
        // Common logback-specific methods
        return methodName.equals("setLevel") ||
               methodName.equals("addAppender") ||
               methodName.equals("detachAppender") ||
               methodName.equals("getAppender") ||
               methodName.equals("callAppenders");
    }
}