package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import java.util.*;

/**
 * Generic transformation rule for Flyway API breaking change (v9+).
 * 
 * Transforms:
 *   Old: new Flyway() + setters pattern
 *   New: Flyway.configure() fluent pattern
 * 
 * Pattern matching:
 * - Constructor: new Flyway()
 * - Setters: setXxx() methods
 * - Transformation:
 *   1. Replace constructor with Flyway.configure()
 *   2. Convert setXxx() to fluent xxx() methods
 *   3. Handle special case: setClassLoader() → configure(ClassLoader)
 *   4. Add .load() at end
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Applies Flyway API migration transformation to all Java files in directory");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to org.flywaydb.core.Flyway
        List<CtConstructorCall> flywayConstructors = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                CtTypeReference typeRef = element.getType();
                if (typeRef == null) return false;
                String qualifiedName = typeRef.getQualifiedName();
                return qualifiedName != null && qualifiedName.equals("org.flywaydb.core.Flyway");
            }
        });
        
        System.out.println("Found " + flywayConstructors.size() + " Flyway constructor calls");
        
        for (CtConstructorCall constructorCall : flywayConstructors) {
            transformFlywayUsage(constructorCall);
        }
        
        // Apply transformations
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete");
    }
    
    private static void transformFlywayUsage(CtConstructorCall constructorCall) {
        Factory factory = constructorCall.getFactory();
        
        // Get the parent statement
        CtStatement parentStatement = constructorCall.getParent(CtStatement.class);
        if (parentStatement == null) return;
        
        // Get variable name
        final String varName;
        if (parentStatement instanceof CtLocalVariable) {
            varName = ((CtLocalVariable) parentStatement).getSimpleName();
        } else if (parentStatement instanceof CtAssignment) {
            CtAssignment assignment = (CtAssignment) parentStatement;
            if (assignment.getAssigned() instanceof CtVariableWrite) {
                varName = ((CtVariableWrite) assignment.getAssigned()).getVariable().getSimpleName();
            } else {
                return;
            }
        } else {
            return;
        }
        
        // Find all method calls on this variable in the same block
        CtBlock block = constructorCall.getParent(CtBlock.class);
        if (block == null) return;
        
        List<CtInvocation> setters = new ArrayList<>();
        boolean foundConstructor = false;
        CtExpression classLoaderArg = null;
        
        for (CtStatement statement : block.getStatements()) {
            if (!foundConstructor) {
                if (statement.toString().contains("new Flyway()")) {
                    foundConstructor = true;
                }
                continue;
            }
            
            // Look for invocations on our variable
            List<CtInvocation> invocations = statement.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    CtExpression target = element.getTarget();
                    if (target instanceof CtVariableRead) {
                        String targetVarName = ((CtVariableRead) target).getVariable().getSimpleName();
                        return targetVarName.equals(varName);
                    }
                    return false;
                }
            });
            
            for (CtInvocation invocation : invocations) {
                String methodName = invocation.getExecutable().getSimpleName();
                if (methodName.equals("setClassLoader")) {
                    // Special handling for setClassLoader
                    List<CtExpression> args = invocation.getArguments();
                    if (!args.isEmpty()) {
                        classLoaderArg = args.get(0);
                    }
                    // Remove the setClassLoader statement
                    CtStatement setterStatement = invocation.getParent(CtStatement.class);
                    if (setterStatement != null) {
                        setterStatement.delete();
                    }
                } else if (methodName.startsWith("set")) {
                    setters.add(invocation);
                }
            }
        }
        
        // Build the fluent configuration chain
        CtTypeReference flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
        CtTypeReference fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
        
        // Create Flyway.configure() or Flyway.configure(classLoader)
        CtExecutableReference configureRef;
        CtInvocation configureInvocation;
        
        if (classLoaderArg != null) {
            // Flyway.configure(classLoader)
            configureRef = factory.Executable().createReference(
                flywayType,
                null,
                "configure",
                new CtTypeReference[0] // Will match any ClassLoader
            );
            configureInvocation = factory.createInvocation(null, configureRef, classLoaderArg);
        } else {
            // Flyway.configure()
            configureRef = factory.Executable().createReference(
                flywayType,
                null,
                "configure",
                new CtTypeReference[0]
            );
            configureInvocation = factory.createInvocation(null, configureRef);
        }
        
        CtExpression fluentExpr = configureInvocation;
        
        // Transform each setter to fluent method
        for (CtInvocation setter : setters) {
            String methodName = setter.getExecutable().getSimpleName();
            if (!methodName.startsWith("set")) continue;
            
            String fluentMethodName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            
            List<CtExpression> args = setter.getArguments();
            
            CtExecutableReference fluentMethodRef = factory.Executable().createReference(
                fluentConfigType,
                null,
                fluentMethodName,
                new CtTypeReference[0]
            );
            
            CtInvocation fluentInvocation = factory.createInvocation(
                fluentExpr,
                fluentMethodRef,
                args.toArray(new CtExpression[0])
            );
            
            fluentExpr = fluentInvocation;
            
            // Remove the setter statement
            CtStatement setterStatement = setter.getParent(CtStatement.class);
            if (setterStatement != null) {
                setterStatement.delete();
            }
        }
        
        // Add .load() at the end
        CtExecutableReference loadRef = factory.Executable().createReference(
            fluentConfigType,
            null,
            "load",
            new CtTypeReference[0]
        );
        CtInvocation loadInvocation = factory.createInvocation(fluentExpr, loadRef);
        
        // Replace the constructor call with the fluent chain
        parentStatement.replace(loadInvocation);
    }
}