package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation for Datafaker 1.4.0 breaking change.
 * 
 * BREAKING CHANGE: DateAndTime.between(Date, Date) changed to DateAndTime.between(Timestamp, Timestamp)
 * 
 * This transformation handles:
 * 1. Method overrides in subclasses of DateAndTime that need parameter type updates
 * 2. Simplification of method bodies that create new Timestamp objects unnecessarily
 * 
 * The transformation is parameterized by:
 * - TARGET_CLASS: Fully qualified class name (net.datafaker.DateAndTime)
 * - TARGET_METHOD: Method name (between)
 * - OLD_PARAM_TYPE: Old parameter type (java.util.Date)
 * - NEW_PARAM_TYPE: New parameter type (java.sql.Timestamp)
 * 
 * To make this transformation reusable for other breaking changes:
 * 1. Update the constants below
 * 2. The transformation will find and update method overrides
 * 3. The transformation will simplify redundant object creation patterns
 * 
 * Usage: java -jar spoon-transformation.jar <source-directory>
 */
public class Main {
    // CONFIGURATION - Make these command-line arguments for full reusability
    private static final String TARGET_CLASS = "net.datafaker.DateAndTime";
    private static final String TARGET_METHOD = "between";
    private static final String OLD_PARAM_TYPE = "java.util.Date";
    private static final String NEW_PARAM_TYPE = "java.sql.Timestamp";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.printf("Transforming %s.%s(%s, %s) -> (%s, %s)%n",
                TARGET_CLASS, TARGET_METHOD, OLD_PARAM_TYPE, OLD_PARAM_TYPE,
                NEW_PARAM_TYPE, NEW_PARAM_TYPE);
        System.out.println("Source directory: " + sourceDir);
        
        try {
            Launcher launcher = createLauncher(sourceDir);
            CtModel model = launcher.buildModel();
            
            transformMethodOverrides(model, launcher);
            
            // Note: Transforming method invocations would require more sophisticated
            // type inference to determine when Date arguments need conversion to Timestamp
            // This is left as future work
            
            launcher.setSourceOutputDirectory("./output");
            launcher.prettyprint();
            
            System.out.println("\nTransformation complete. Output written to ./output");
            System.out.println("Summary:");
            System.out.println("- Updated method overrides in subclasses of " + TARGET_CLASS);
            System.out.println("- Changed parameter types from " + OLD_PARAM_TYPE + " to " + NEW_PARAM_TYPE);
            System.out.println("- Simplified redundant new " + NEW_PARAM_TYPE + "(...getTime()) patterns");
            
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
        System.err.println();
        System.err.println("Transforms breaking API changes in Java source code.");
        System.err.println("Current configuration:");
        System.err.printf("  Class: %s%n", TARGET_CLASS);
        System.err.printf("  Method: %s%n", TARGET_METHOD);
        System.err.printf("  Parameter change: %s -> %s%n", OLD_PARAM_TYPE, NEW_PARAM_TYPE);
        System.err.println();
        System.err.println("To customize for other breaking changes, modify the constants in Main.java");
    }
    
    private static Launcher createLauncher(String sourceDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        return launcher;
    }
    
    private static void transformMethodOverrides(CtModel model, Launcher launcher) {
        List<CtClass<?>> subclasses = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> ctClass) {
                CtTypeReference<?> superClass = ctClass.getSuperclass();
                if (superClass == null) return false;
                return TARGET_CLASS.equals(superClass.getQualifiedName());
            }
        });
        
        System.out.println("\nFound " + subclasses.size() + " subclass(es) of " + TARGET_CLASS);
        
        for (CtClass<?> subclass : subclasses) {
            System.out.println("Processing: " + subclass.getQualifiedName());
            
            for (CtMethod<?> method : subclass.getMethods()) {
                if (TARGET_METHOD.equals(method.getSimpleName())) {
                    List<CtParameter<?>> params = method.getParameters();
                    if (params.size() == 2) {
                        CtParameter<?> param1 = params.get(0);
                        CtParameter<?> param2 = params.get(1);
                        
                        if (OLD_PARAM_TYPE.equals(param1.getType().getQualifiedName()) &&
                            OLD_PARAM_TYPE.equals(param2.getType().getQualifiedName())) {
                            
                            System.out.println("  Updating: " + method.getSignature());
                            updateMethodSignature(method, launcher);
                            simplifyMethodBody(method, launcher);
                        }
                    }
                }
            }
        }
    }
    
    private static void updateMethodSignature(CtMethod<?> method, Launcher launcher) {
        // Change parameter types from OLD_PARAM_TYPE to NEW_PARAM_TYPE
        CtTypeReference<?> newType = launcher.getFactory().Type().createReference(NEW_PARAM_TYPE);
        for (CtParameter<?> param : method.getParameters()) {
            param.setType(newType);
        }
        
        // Update parameter names for clarity
        updateParameterNames(method.getParameters());
    }
    
    private static void updateParameterNames(List<CtParameter<?>> params) {
        if (params.size() >= 1) {
            CtParameter<?> param1 = params.get(0);
            String name1 = param1.getSimpleName();
            if (name1 != null && name1.toLowerCase().contains("date")) {
                param1.setSimpleName(name1.replaceAll("(?i)date", "timestamp"));
            }
        }
        if (params.size() >= 2) {
            CtParameter<?> param2 = params.get(1);
            String name2 = param2.getSimpleName();
            if (name2 != null && name2.toLowerCase().contains("date")) {
                param2.setSimpleName(name2.replaceAll("(?i)date", "timestamp"));
            }
        }
    }
    
    private static void simplifyMethodBody(CtMethod<?> method, Launcher launcher) {
        CtBlock<?> body = method.getBody();
        if (body == null) return;
        
        // Look for pattern: new Timestamp(super.between(...).getTime())
        // This pattern is redundant when super.between() already returns Timestamp
        List<CtInvocation<?>> superCalls = body.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                return execRef != null && TARGET_METHOD.equals(execRef.getSimpleName());
            }
        });
        
        for (CtInvocation<?> invocation : superCalls) {
            if (isWrappedInTimestampGetTime(invocation)) {
                // Replace new Timestamp(super.between(...).getTime()) with super.between(...)
                invocation.getParent(CtConstructorCall.class).replace(invocation);
                System.out.println("    Simplified redundant new " + NEW_PARAM_TYPE + "(...getTime())");
            }
        }
    }
    
    private static boolean isWrappedInTimestampGetTime(CtInvocation<?> invocation) {
        // Check if invocation is wrapped in: new Timestamp(...getTime())
        CtExpression<?> parent = invocation.getParent(CtExpression.class);
        
        if (parent instanceof spoon.reflect.code.CtInvocation) {
            spoon.reflect.code.CtInvocation<?> getTimeCall = (spoon.reflect.code.CtInvocation<?>) parent;
            if ("getTime".equals(getTimeCall.getExecutable().getSimpleName())) {
                CtExpression<?> grandParent = getTimeCall.getParent(CtExpression.class);
                if (grandParent instanceof CtConstructorCall) {
                    CtConstructorCall<?> ctorCall = (CtConstructorCall<?>) grandParent;
                    return NEW_PARAM_TYPE.equals(ctorCall.getType().getQualifiedName());
                }
            }
        }
        return false;
    }
}