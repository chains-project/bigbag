package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.*;
import spoon.support.reflect.code.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-dir> <output-dir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying Flyway API migration transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Create the transformation
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Run the transformation
        launcher.addProcessor(new FlywayApiMigrationProcessor());
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
    }
}

class FlywayApiMigrationProcessor extends spoon.processing.AbstractProcessor<CtClass<?>> {
    
    @Override
    public void process(CtClass<?> clazz) {
        // Find all constructor calls to org.flywaydb.core.Flyway
        List<CtConstructorCall<?>> flywayConstructors = clazz.getElements(
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructor) {
                    CtTypeReference<?> typeRef = constructor.getType();
                    return typeRef != null && 
                           "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName());
                }
            }
        );
        
        for (CtConstructorCall<?> constructor : flywayConstructors) {
            transformFlywayConstructor(constructor);
        }
    }
    
    private void transformFlywayConstructor(CtConstructorCall<?> constructor) {
        // Get the parent statement (usually a variable assignment or declaration)
        CtStatement parentStatement = constructor.getParent(CtStatement.class);
        if (parentStatement == null) return;
        
        // Get the variable that the Flyway instance is assigned to
        CtVariable<?> flywayVariable = findAssignedVariable(parentStatement);
        if (flywayVariable == null) return;
        
        // Find all setter method calls on this Flyway variable
        List<CtInvocation<?>> setterCalls = findSetterCalls(flywayVariable);
        
        // Create the new Flyway.configure()...load() expression
        CtExpression<?> newFlywayExpression = createNewFlywayExpression(setterCalls);
        
        // Replace the constructor call with the new expression
        replaceConstructorWithNewExpression(constructor, newFlywayExpression, flywayVariable, setterCalls);
    }
    
    private CtVariable<?> findAssignedVariable(CtStatement statement) {
        if (statement instanceof CtLocalVariable) {
            return (CtLocalVariable<?>) statement;
        } else if (statement instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) statement;
            if (assignment.getAssigned() instanceof CtVariableWrite) {
                CtVariableWrite<?> varWrite = (CtVariableWrite<?>) assignment.getAssigned();
                return varWrite.getVariable().getDeclaration();
            }
        } else if (statement instanceof CtReturn) {
            // For return statements, we need to find the variable in the method scope
            CtExecutable<?> parentMethod = statement.getParent(CtExecutable.class);
            if (parentMethod != null) {
                // Look for a local variable declaration that assigns the Flyway instance
                for (CtStatement stmt : parentMethod.getBody().getStatements()) {
                    if (stmt instanceof CtLocalVariable) {
                        CtLocalVariable<?> localVar = (CtLocalVariable<?>) stmt;
                        if (localVar.getDefaultExpression() instanceof CtConstructorCall) {
                            CtConstructorCall<?> ctor = (CtConstructorCall<?>) localVar.getDefaultExpression();
                            if ("org.flywaydb.core.Flyway".equals(ctor.getType().getQualifiedName())) {
                                return localVar;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private List<CtInvocation<?>> findSetterCalls(CtVariable<?> variable) {
        List<CtInvocation<?>> setters = new ArrayList<>();
        String varName = variable.getSimpleName();
        
        // Get the method containing the variable
        CtExecutable<?> parentMethod = variable.getParent(CtExecutable.class);
        if (parentMethod == null) return setters;
        
        // Find all method invocations on this variable
        List<CtInvocation<?>> allInvocations = parentMethod.getElements(
            new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    CtExpression<?> target = invocation.getTarget();
                    return target != null && 
                           target.toString().equals(varName) &&
                           invocation.getExecutable().getSimpleName().startsWith("set");
                }
            }
        );
        
        // Sort by position in source code
        allInvocations.sort(Comparator.comparingInt(inv -> inv.getPosition().getSourceStart()));
        return allInvocations;
    }
    
    private CtExpression<?> createNewFlywayExpression(List<CtInvocation<?>> setterCalls) {
        // Start with Flyway.configure()
        CtTypeReference<?> flywayType = getFactory().Type().createReference("org.flywaydb.core.Flyway");
        CtInvocation<?> configureCall = getFactory().createInvocation(
            null,
            getFactory().createTypeAccess(flywayType),
            getFactory().createExecutableReference().setSimpleName("configure")
        );
        
        CtExpression<?> currentExpression = configureCall;
        
        // Chain the setter calls as fluent builder calls
        for (CtInvocation<?> setter : setterCalls) {
            String setterName = setter.getExecutable().getSimpleName();
            String fluentMethodName = setterName.substring(3); // Remove "set" prefix
            fluentMethodName = Character.toLowerCase(fluentMethodName.charAt(0)) + fluentMethodName.substring(1);
            
            List<CtExpression<?>> arguments = new ArrayList<>(setter.getArguments());
            
            CtInvocation<?> fluentCall = getFactory().createInvocation(
                currentExpression.clone(),
                getFactory().createExecutableReference().setSimpleName(fluentMethodName),
                arguments
            );
            
            currentExpression = fluentCall;
        }
        
        // Add .load() at the end
        CtInvocation<?> loadCall = getFactory().createInvocation(
            currentExpression,
            getFactory().createExecutableReference().setSimpleName("load")
        );
        
        return loadCall;
    }
    
    private void replaceConstructorWithNewExpression(CtConstructorCall<?> constructor, 
                                                    CtExpression<?> newExpression,
                                                    CtVariable<?> flywayVariable,
                                                    List<CtInvocation<?>> setterCalls) {
        // Replace the constructor call in the variable declaration/assignment
        if (flywayVariable instanceof CtLocalVariable) {
            CtLocalVariable<?> localVar = (CtLocalVariable<?>) flywayVariable;
            localVar.setDefaultExpression(newExpression);
        } else if (flywayVariable.getParent() instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) flywayVariable.getParent();
            assignment.setAssignment(newExpression);
        }
        
        // Remove all setter calls since they're now part of the fluent builder chain
        for (CtInvocation<?> setter : setterCalls) {
            CtStatement setterStatement = setter.getParent(CtStatement.class);
            if (setterStatement != null) {
                setterStatement.delete();
            }
        }
    }
}