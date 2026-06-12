package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.*;
import spoon.support.reflect.code.*;
import java.util.*;

public class SimpleFlywayTransformation {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.SimpleFlywayTransformation <source-dir> <output-dir>");
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
        
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new FlywayApiSimpleProcessor());
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
    }
}

class FlywayApiSimpleProcessor extends spoon.processing.AbstractProcessor<CtConstructorCall<?>> {
    
    @Override
    public void process(CtConstructorCall<?> constructor) {
        CtTypeReference<?> typeRef = constructor.getType();
        if (typeRef == null || !"org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName())) {
            return;
        }
        
        System.out.println("Found Flyway constructor at: " + constructor.getPosition());
        
        CtStatement parentStatement = constructor.getParent(CtStatement.class);
        if (parentStatement == null) {
            System.out.println("  Warning: No parent statement found");
            return;
        }
        
        CtVariable<?> flywayVariable = findAssignedVariable(parentStatement);
        if (flywayVariable == null) {
            System.out.println("  Warning: Could not find assigned variable");
            return;
        }
        
        System.out.println("  Variable: " + flywayVariable.getSimpleName());
        
        List<CtInvocation<?>> setterCalls = findSetterCalls(flywayVariable);
        System.out.println("  Found " + setterCalls.size() + " setter calls");
        
        if (setterCalls.isEmpty()) {
            System.out.println("  Simple case: new Flyway() -> Flyway.configure().load()");
            replaceWithSimpleConfigure(constructor, flywayVariable);
        } else {
            System.out.println("  Complex case with setters");
            replaceWithConfigureChain(constructor, flywayVariable, setterCalls);
        }
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
            CtExecutable<?> parentMethod = statement.getParent(CtExecutable.class);
            if (parentMethod != null) {
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
        
        CtExecutable<?> parentMethod = variable.getParent(CtExecutable.class);
        if (parentMethod == null) return setters;
        
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
        
        allInvocations.sort(Comparator.comparingInt(inv -> inv.getPosition().getSourceStart()));
        return allInvocations;
    }
    
    private void replaceWithSimpleConfigure(CtConstructorCall<?> constructor, CtVariable<?> variable) {
        try {
            CtTypeReference<?> flywayType = getFactory().Type().createReference("org.flywaydb.core.Flyway");
            
            CtInvocation<?> configureCall = getFactory().createInvocation(
                getFactory().createTypeAccess(flywayType),
                getFactory().createExecutableReference().setSimpleName("configure")
            );
            
            CtInvocation<?> loadCall = getFactory().createInvocation(
                configureCall,
                getFactory().createExecutableReference().setSimpleName("load")
            );
            
            if (variable instanceof CtLocalVariable) {
                CtLocalVariable<?> localVar = (CtLocalVariable<?>) variable;
                localVar.setDefaultExpression(loadCall);
            } else if (variable.getParent() instanceof CtAssignment) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) variable.getParent();
                assignment.setAssignment(loadCall);
            }
            
            System.out.println("  Replaced with simple configure().load()");
        } catch (Exception e) {
            System.err.println("  Error during transformation: " + e.getMessage());
        }
    }
    
    private void replaceWithConfigureChain(CtConstructorCall<?> constructor, CtVariable<?> variable, List<CtInvocation<?>> setterCalls) {
        try {
            CtTypeReference<?> flywayType = getFactory().Type().createReference("org.flywaydb.core.Flyway");
            
            CtInvocation<?> configureCall = getFactory().createInvocation(
                getFactory().createTypeAccess(flywayType),
                getFactory().createExecutableReference().setSimpleName("configure")
            );
            
            CtExpression<?> currentExpression = configureCall;
            
            for (CtInvocation<?> setter : setterCalls) {
                String setterName = setter.getExecutable().getSimpleName();
                String fluentMethodName = setterName.substring(3);
                fluentMethodName = Character.toLowerCase(fluentMethodName.charAt(0)) + fluentMethodName.substring(1);
                
                List<CtExpression<?>> arguments = new ArrayList<>(setter.getArguments());
                
                CtInvocation<?> fluentCall = getFactory().createInvocation(
                    currentExpression,
                    getFactory().createExecutableReference().setSimpleName(fluentMethodName),
                    arguments.toArray(new CtExpression[0])
                );
                
                currentExpression = fluentCall;
            }
            
            CtInvocation<?> loadCall = getFactory().createInvocation(
                currentExpression,
                getFactory().createExecutableReference().setSimpleName("load")
            );
            
            if (variable instanceof CtLocalVariable) {
                CtLocalVariable<?> localVar = (CtLocalVariable<?>) variable;
                localVar.setDefaultExpression(loadCall);
            } else if (variable.getParent() instanceof CtAssignment) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) variable.getParent();
                assignment.setAssignment(loadCall);
            }
            
            for (CtInvocation<?> setter : setterCalls) {
                CtStatement setterStatement = setter.getParent(CtStatement.class);
                if (setterStatement != null) {
                    setterStatement.delete();
                }
            }
            
            System.out.println("  Replaced with configure() chain ending with .load()");
        } catch (Exception e) {
            System.err.println("  Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}