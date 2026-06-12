package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    
    // Configuration: These are the only project-specific values that need to be changed
    private static final String FLYWAY_FULLY_QUALIFIED_NAME = "org.flywaydb.core.Flyway";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying Flyway API migration transformation to: " + sourceDirectory);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDirectory);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            CtModel model = launcher.buildModel();
            
            // Find all constructor calls to Flyway
            List<CtConstructorCall<?>> flywayConstructorCalls = model
                .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> constructorCall) {
                        CtTypeReference<?> typeRef = constructorCall.getType();
                        return typeRef != null && 
                               FLYWAY_FULLY_QUALIFIED_NAME.equals(typeRef.getQualifiedName()) &&
                               constructorCall.getArguments().isEmpty(); // No-arg constructor
                    }
                });
            
            System.out.println("Found " + flywayConstructorCalls.size() + " Flyway constructor calls to transform");
            
            int transformationCount = 0;
            for (CtConstructorCall<?> constructorCall : flywayConstructorCalls) {
                if (transformFlywayConstructorCall(constructorCall)) {
                    transformationCount++;
                }
            }
            
            System.out.println("Successfully transformed " + transformationCount + " Flyway constructor calls");
            
            // Save the transformed code
            launcher.setSourceOutputDirectory(sourceDirectory);
            launcher.prettyprint();
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean transformFlywayConstructorCall(CtConstructorCall<?> constructorCall) {
        try {
            // Get the parent statement
            CtStatement parentStatement = constructorCall.getParent(CtStatement.class);
            if (parentStatement == null) {
                return false;
            }
            
            // Find all setter invocations in the same method/block
            List<CtInvocation<?>> setterInvocations = findSetterInvocationsInScope(parentStatement, constructorCall);
            
            // Build the new fluent configuration expression
            String newExpression = buildFluentConfigurationExpression(setterInvocations);
            
            // Replace constructor call with new expression
            CtExpression<?> parsedExpression = constructorCall.getFactory().Code().createCodeSnippetExpression(newExpression);
            constructorCall.replace(parsedExpression);
            
            // Delete setter invocations
            for (CtInvocation<?> invocation : setterInvocations) {
                CtStatement invocationStatement = invocation.getParent(CtStatement.class);
                if (invocationStatement != null) {
                    invocationStatement.delete();
                }
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error transforming constructor call at " + 
                constructorCall.getPosition().toString() + ": " + e.getMessage());
            return false;
        }
    }
    
    private static List<CtInvocation<?>> findSetterInvocationsInScope(CtStatement statement, CtConstructorCall<?> constructorCall) {
        List<CtInvocation<?>> invocations = new ArrayList<>();
        
        // Get the containing method
        CtMethod<?> parentMethod = statement.getParent(CtMethod.class);
        if (parentMethod == null || parentMethod.getBody() == null) {
            return invocations;
        }
        
        // Find all invocations in the method body
        List<CtInvocation<?>> allInvocations = parentMethod.getBody().getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        // Map to track which variable the constructor creates
        String targetVariableName = extractVariableName(statement);
        
        for (CtInvocation<?> invocation : allInvocations) {
            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                String targetStr = target.toString();
                // Check if this invocation targets the same variable created by our constructor
                if (targetVariableName != null && targetStr.contains(targetVariableName)) {
                    String methodName = invocation.getExecutable().getSimpleName();
                    if (methodName.startsWith("set") && invocation.getArguments().size() == 1) {
                        invocations.add(invocation);
                    }
                }
            }
        }
        
        return invocations;
    }
    
    private static String extractVariableName(CtStatement statement) {
        String statementStr = statement.toString();
        // Simple extraction - look for pattern like "Flyway flyway = " or "flyway = "
        if (statementStr.contains("Flyway")) {
            String[] parts = statementStr.split("=")[0].trim().split("\\s+");
            if (parts.length > 0) {
                return parts[parts.length - 1];
            }
        }
        return null;
    }
    
    private static String buildFluentConfigurationExpression(List<CtInvocation<?>> setterInvocations) {
        StringBuilder sb = new StringBuilder();
        sb.append(FLYWAY_FULLY_QUALIFIED_NAME).append(".configure()");
        
        // Map old setter names to new fluent method names
        Map<String, String> methodMapping = new HashMap<>();
        methodMapping.put("setDataSource", "dataSource");
        methodMapping.put("setLocations", "locations");
        methodMapping.put("setValidateOnMigrate", "validateOnMigrate");
        
        // Special handling for classLoader - it goes in configure() method
        String classLoaderArg = null;
        
        for (CtInvocation<?> invocation : setterInvocations) {
            String methodName = invocation.getExecutable().getSimpleName();
            CtExpression<?> arg = invocation.getArguments().get(0);
            String argCode = arg.toString();
            
            if ("setClassLoader".equals(methodName)) {
                classLoaderArg = argCode;
            } else if (methodMapping.containsKey(methodName)) {
                String newMethodName = methodMapping.get(methodName);
                if (newMethodName != null) {
                    // Handle locations specially - it might need to be split
                    if ("locations".equals(newMethodName) && argCode.contains(",")) {
                        // Split comma-separated locations into array
                        sb.append(".").append(newMethodName).append("(").append(argCode).append(".split(\",\"))");
                    } else {
                        sb.append(".").append(newMethodName).append("(").append(argCode).append(")");
                    }
                }
            }
        }
        
        // If we have a classLoader, use configure(ClassLoader) instead of configure()
        if (classLoaderArg != null) {
            String baseExpression = FLYWAY_FULLY_QUALIFIED_NAME + ".configure(" + classLoaderArg + ")";
            // Replace the initial configure() call
            String current = sb.toString();
            String configured = current.replaceFirst(FLYWAY_FULLY_QUALIFIED_NAME + "\\.configure\\(\\)", baseExpression);
            sb = new StringBuilder(configured);
        }
        
        sb.append(".load()");
        return sb.toString();
    }
}