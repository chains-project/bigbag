package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.*;
import spoon.processing.AbstractProcessor;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source-directory>");
            System.out.println("Example: java Main /path/to/project/src");
            return;
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setComplianceLevel(11);
        
        launcher.addProcessor(new FlywayApiMigrationProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

class FlywayApiMigrationProcessor extends AbstractProcessor<CtConstructorCall<?>> {
    
    @Override
    public void process(CtConstructorCall<?> constructorCall) {
        if (!isFlywayConstructorCall(constructorCall)) {
            return;
        }
        
        System.out.println("Found Flyway constructor call at: " + 
            constructorCall.getPosition().toString());
        
        CtVariable<?> flywayVariable = findAssignedVariable(constructorCall);
        if (flywayVariable == null) {
            System.out.println("Warning: Flyway constructor not assigned to a variable, skipping");
            return;
        }
        
        List<CtInvocation<?>> setterCalls = findSetterCalls(flywayVariable);
        System.out.println("Found " + setterCalls.size() + " setter calls for Flyway instance");
        
        try {
            transformFlywayInstantiation(constructorCall, flywayVariable, setterCalls);
            
            for (CtInvocation<?> call : setterCalls) {
                call.delete();
            }
            
            System.out.println("Successfully transformed Flyway instantiation");
        } catch (Exception e) {
            System.err.println("Error transforming Flyway instantiation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private boolean isFlywayConstructorCall(CtConstructorCall<?> call) {
        CtTypeReference<?> typeRef = call.getType();
        if (typeRef == null) {
            return false;
        }
        String typeName = typeRef.getQualifiedName();
        return "org.flywaydb.core.Flyway".equals(typeName) ||
               "Flyway".equals(typeRef.getSimpleName());
    }
    
    private CtVariable<?> findAssignedVariable(CtConstructorCall<?> constructorCall) {
        CtElement parent = constructorCall.getParent();
        
        if (parent instanceof CtLocalVariable) {
            return (CtLocalVariable<?>) parent;
        } else if (parent instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parent;
            CtExpression<?> assigned = assignment.getAssigned();
            if (assigned instanceof CtVariableAccess) {
                return ((CtVariableAccess<?>) assigned).getVariable().getDeclaration();
            }
        }
        
        return null;
    }
    
    private List<CtInvocation<?>> findSetterCalls(CtVariable<?> variable) {
        List<CtInvocation<?>> setterCalls = new ArrayList<>();
        CtBlock<?> block = getEnclosingBlock(variable);
        
        if (block == null) {
            return setterCalls;
        }
        
        for (CtStatement statement : block.getStatements()) {
            if (statement instanceof CtInvocation<?>) {
                CtInvocation<?> invocation = (CtInvocation<?>) statement;
                CtExpression<?> target = invocation.getTarget();
                
                if (target instanceof CtVariableAccess) {
                    if (((CtVariableAccess<?>) target).getVariable().getDeclaration() == variable && 
                        isSetterCall(invocation)) {
                        setterCalls.add(invocation);
                    }
                }
            }
        }
        
        return setterCalls;
    }
    
    private CtBlock<?> getEnclosingBlock(CtElement element) {
        CtElement current = element;
        while (current != null) {
            if (current instanceof CtBlock) {
                return (CtBlock<?>) current;
            }
            current = current.getParent();
        }
        return null;
    }
    
    private boolean isSetterCall(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        return methodName.startsWith("set") && 
               (methodName.equals("setDataSource") || 
                methodName.equals("setClassLoader") ||
                methodName.equals("setLocations") ||
                methodName.equals("setValidateOnMigrate"));
    }
    
    private void transformFlywayInstantiation(
            CtConstructorCall<?> constructorCall, 
            CtVariable<?> variable, 
            List<CtInvocation<?>> setterCalls) {
        
        Factory factory = getFactory();
        
        Map<String, CtExpression<?>> methodArgs = new LinkedHashMap<>();
        boolean hasClassLoader = false;
        CtExpression<?> classLoaderArg = null;
        
        for (CtInvocation<?> invocation : setterCalls) {
            String methodName = invocation.getExecutable().getSimpleName();
            List<CtExpression<?>> args = invocation.getArguments();
            
            if (args.isEmpty()) {
                continue;
            }
            
            if ("setClassLoader".equals(methodName)) {
                hasClassLoader = true;
                classLoaderArg = args.get(0).clone();
            } else if ("setDataSource".equals(methodName)) {
                methodArgs.put("dataSource", args.get(0).clone());
            } else if ("setLocations".equals(methodName)) {
                methodArgs.put("locations", args.get(0).clone());
            } else if ("setValidateOnMigrate".equals(methodName)) {
                methodArgs.put("validateOnMigrate", args.get(0).clone());
            }
        }
        
        CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
        CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
        
        CtExpression<?> flywayTypeAccess = factory.Code().createTypeAccess(flywayType);
        
        CtExecutableReference<?> configureRef;
        CtInvocation<?> configureInvocation;
        
        if (hasClassLoader && classLoaderArg != null) {
            CtTypeReference<?> classLoaderType = factory.Type().createReference(ClassLoader.class);
            configureRef = factory.Executable().createReference(
                flywayType, fluentConfigType, "configure", classLoaderType
            );
            configureInvocation = factory.Code().createInvocation(flywayTypeAccess, configureRef, classLoaderArg);
        } else {
            configureRef = factory.Executable().createReference(
                flywayType, fluentConfigType, "configure"
            );
            configureInvocation = factory.Code().createInvocation(flywayTypeAccess, configureRef);
        }
        
        CtExpression<?> currentExpression = configureInvocation;
        
        for (Map.Entry<String, CtExpression<?>> entry : methodArgs.entrySet()) {
            String methodName = entry.getKey();
            CtExpression<?> arg = entry.getValue();
            
            CtExecutableReference<?> methodRef = factory.Executable().createReference(
                fluentConfigType, fluentConfigType, methodName
            );
            
            CtInvocation<?> nextInvocation = factory.Code().createInvocation(currentExpression, methodRef, arg);
            currentExpression = (CtExpression<?>) nextInvocation;
        }
        
        CtExecutableReference<?> loadRef = factory.Executable().createReference(
            fluentConfigType, flywayType, "load"
        );
        
        CtInvocation<?> finalInvocation = factory.Code().createInvocation(currentExpression, loadRef);
        
        if (constructorCall.getParent() instanceof CtLocalVariable) {
            CtLocalVariable<?> localVar = (CtLocalVariable<?>) constructorCall.getParent();
            localVar.setAssignment((CtExpression<?>) finalInvocation);
        } else if (constructorCall.getParent() instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) constructorCall.getParent();
            assignment.setAssignment((CtExpression<?>) finalInvocation);
        } else {
            constructorCall.replace(finalInvocation);
        }
    }
}