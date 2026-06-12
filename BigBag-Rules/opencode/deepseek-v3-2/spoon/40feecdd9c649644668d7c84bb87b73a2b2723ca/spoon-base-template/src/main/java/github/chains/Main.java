package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.*;
import java.util.*;

/**
 * Generic Spoon transformation to fix Joda-Time DateMidnight deprecation.
 * 
 * Breaking Change Pattern:
 * - Old API: org.joda.time.DateMidnight (deprecated class)
 * - New API: org.joda.time.LocalDate (for date-only operations) or 
 *            org.joda.time.DateTime.withTimeAtStartOfDay() (for date+time)
 * 
 * This transformation handles:
 * 1. Type references: DateMidnight -> LocalDate
 * 2. Constructor calls: new DateMidnight(...) -> new LocalDate(...)
 * 
 * The transformation is parameterized and generic - it can be applied to any
 * project with the same breaking change by adjusting OLD_TYPE and NEW_TYPE.
 */
public class Main {
    
    // Configuration parameters - can be externalized or made command-line args
    private static final String OLD_TYPE = "org.joda.time.DateMidnight";
    private static final String NEW_TYPE = "org.joda.time.LocalDate";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("  <source-directory>: Path to Java source code to transform");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Replacing " + OLD_TYPE + " with " + NEW_TYPE);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Get the root package
        CtPackage rootPackage = model.getRootPackage();
        
        // Apply transformations
        int typeRefCount = transformTypeReferences(rootPackage);
        int constructorCount = transformConstructorCalls(rootPackage);
        int methodCount = transformMethodCalls(rootPackage);
        
        System.out.println("Transformation summary:");
        System.out.println("  - Type references replaced: " + typeRefCount);
        System.out.println("  - Constructor calls replaced: " + constructorCount);
        System.out.println("  - Method calls replaced: " + methodCount);
        
        // Write transformed code back to a temporary directory first
        String outputDir = sourceDir + "-transformed";
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        
        System.out.println("Transformed code written to: " + outputDir);
        
        System.out.println("Transformation completed.");
    }
    
    /**
     * Transform type references from OLD_TYPE to NEW_TYPE
     * Returns count of transformations applied
     */
    private static int transformTypeReferences(CtElement root) {
        int count = 0;
        
        // Find all type references to OLD_TYPE
        List<CtTypeReference<?>> typeRefs = Query.getElements(root, 
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return OLD_TYPE.equals(typeRef.getQualifiedName());
                }
            });
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            System.out.println("Transforming type reference at: " + typeRef.getPosition());
            
            // Create new type reference
            CtTypeReference<?> newTypeRef = typeRef.getFactory().Type().createReference(NEW_TYPE);
            
            // Replace based on parent type
            if (typeRef.getParent() instanceof CtVariable) {
                CtVariable<?> var = (CtVariable<?>) typeRef.getParent();
                var.setType(newTypeRef);
                count++;
            } else if (typeRef.getParent() instanceof CtField) {
                CtField<?> field = (CtField<?>) typeRef.getParent();
                field.setType(newTypeRef);
                count++;
            } else if (typeRef.getParent() instanceof CtMethod) {
                CtMethod<?> method = (CtMethod<?>) typeRef.getParent();
                if (typeRef.equals(method.getType())) {
                    method.setType(newTypeRef);
                    count++;
                }
            } else if (typeRef.getParent() instanceof CtParameter) {
                CtParameter<?> param = (CtParameter<?>) typeRef.getParent();
                param.setType(newTypeRef);
                count++;
            } else if (typeRef.getParent() instanceof CtTypeReference) {
                // Handle nested type references (e.g., List<DateMidnight>)
                System.out.println("  Warning: Skipping nested type reference at: " + typeRef.getPosition());
            } else {
                System.out.println("  Warning: Unhandled parent type for type reference at: " + typeRef.getPosition());
            }
        }
        
        return count;
    }
    
    /**
     * Transform constructor calls from new DateMidnight(...) to new LocalDate(...)
     * Returns count of transformations applied
     */
    private static int transformConstructorCalls(CtElement root) {
        int count = 0;
        
        // Find all constructor calls to OLD_TYPE
        List<CtConstructorCall<?>> constructorCalls = Query.getElements(root,
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    CtTypeReference<?> constructedType = constructorCall.getType();
                    return constructedType != null && 
                           OLD_TYPE.equals(constructedType.getQualifiedName());
                }
            });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            System.out.println("Transforming constructor call at: " + constructorCall.getPosition());
            
            // Get the factory
            spoon.reflect.factory.Factory factory = constructorCall.getFactory();
            
            // Replace with new LocalDate(...)
            CtTypeReference<?> newType = factory.Type().createReference(NEW_TYPE);
            CtConstructorCall<?> newConstructorCall = factory.createConstructorCall(newType);
            
            // Copy arguments
            for (CtExpression<?> arg : constructorCall.getArguments()) {
                newConstructorCall.addArgument(arg.clone());
            }
            
            // Replace the constructor call
            constructorCall.replace(newConstructorCall);
            count++;
        }
        
        return count;
    }
    
    /**
     * Transform deprecated method calls like toYearMonthDay() and toTimeOfDay()
     * Returns count of transformations applied
     */
    private static int transformMethodCalls(CtElement root) {
        int count = 0;
        
        // Find method calls to deprecated methods
        List<CtInvocation<?>> methodCalls = Query.getElements(root,
            new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    String methodName = invocation.getExecutable().getSimpleName();
                    // These are known deprecated methods in Joda-Time
                    return "toYearMonthDay".equals(methodName) || 
                           "toTimeOfDay".equals(methodName);
                }
            });
        
        for (CtInvocation<?> methodCall : methodCalls) {
            String methodName = methodCall.getExecutable().getSimpleName();
            System.out.println("Transforming method call " + methodName + " at: " + methodCall.getPosition());
            
            spoon.reflect.factory.Factory factory = methodCall.getFactory();
            
            if ("toYearMonthDay".equals(methodName)) {
                // Replace toYearMonthDay() with toLocalDate()
                CtExecutableReference<?> newMethodRef = factory.createExecutableReference();
                newMethodRef.setSimpleName("toLocalDate");
                // The declaring type depends on the target, but we set it generically
                newMethodRef.setDeclaringType(factory.Type().createReference(NEW_TYPE));
                
                methodCall.getExecutable().replace(newMethodRef);
                count++;
            } else if ("toTimeOfDay".equals(methodName)) {
                // Replace toTimeOfDay() with toLocalTime()
                CtExecutableReference<?> newMethodRef = factory.createExecutableReference();
                newMethodRef.setSimpleName("toLocalTime");
                newMethodRef.setDeclaringType(factory.Type().createReference("org.joda.time.LocalTime"));
                
                methodCall.getExecutable().replace(newMethodRef);
                count++;
            }
        }
        
        return count;
    }
}