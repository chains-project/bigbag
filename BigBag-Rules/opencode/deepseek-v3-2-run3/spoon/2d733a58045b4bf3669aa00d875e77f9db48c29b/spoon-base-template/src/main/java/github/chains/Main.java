package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        
        try {
            launcher.buildModel();
        } catch (Exception e) {
            System.err.println("Error building model: " + e.getMessage());
            System.exit(1);
        }
        
        Factory factory = launcher.getFactory();
        
        // Map of old setter method names to new builder method names
        Map<String, String> methodTransforms = new HashMap<>();
        methodTransforms.put("setDataSource", "dataSource");
        methodTransforms.put("setClassLoader", "classLoader");
        methodTransforms.put("setLocations", "locations");
        methodTransforms.put("setValidateOnMigrate", "validateOnMigrate");
        methodTransforms.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        methodTransforms.put("setTable", "table");
        methodTransforms.put("setBaselineOnMigrate", "baselineOnMigrate");
        methodTransforms.put("setBaselineVersion", "baselineVersion");
        methodTransforms.put("setPlaceholderPrefix", "placeholderPrefix");
        methodTransforms.put("setPlaceholderSuffix", "placeholderSuffix");
        methodTransforms.put("setPlaceholderReplacement", "placeholderReplacement");
        methodTransforms.put("setPlaceholders", "placeholders");
        methodTransforms.put("setEncoding", "encoding");
        methodTransforms.put("setSchemas", "schemas");
        methodTransforms.put("setCleanOnValidationError", "cleanOnValidationError");
        methodTransforms.put("setCleanDisabled", "cleanDisabled");
        methodTransforms.put("setMixed", "mixed");
        methodTransforms.put("setGroup", "group");
        methodTransforms.put("setInstalledBy", "installedBy");
        methodTransforms.put("setTarget", "target");
        methodTransforms.put("setOutOfOrder", "outOfOrder");
        methodTransforms.put("setValidateMigrationNaming", "validateMigrationNaming");
        methodTransforms.put("setIgnoreMissingMigrations", "ignoreMissingMigrations");
        methodTransforms.put("setIgnoreIgnoredMigrations", "ignoreIgnoredMigrations");
        methodTransforms.put("setIgnoreFutureMigrations", "ignoreFutureMigrations");
        
        // Find all CtNewClass elements and check if they're Flyway constructors
        List<CtNewClass<?>> allNewClasses = launcher.getModel().getElements(new TypeFilter<CtNewClass<?>>(CtNewClass.class));
        List<CtNewClass<?>> flywayConstructors = new ArrayList<>();
        
        for (CtNewClass<?> newClass : allNewClasses) {
            CtTypeReference<?> typeRef = newClass.getType();
            if (typeRef != null) {
                String typeName = typeRef.getQualifiedName();
                if ("org.flywaydb.core.Flyway".equals(typeName) || 
                    (typeName != null && typeName.endsWith(".Flyway"))) {
                    flywayConstructors.add(newClass);
                }
            }
        }
        
        System.out.println("Found " + flywayConstructors.size() + " Flyway constructor calls to transform");
        
        int transformedCount = 0;
        for (CtNewClass<?> constructor : flywayConstructors) {
            try {
                if (transformFlywayConstructor(factory, constructor, methodTransforms)) {
                    transformedCount++;
                }
            } catch (Exception e) {
                System.err.println("Error transforming constructor at " + constructor.getPosition() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Successfully transformed " + transformedCount + " Flyway constructor calls");
        
        // Save the transformed code
        try {
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error saving transformed code: " + e.getMessage());
        }
    }
    
    private static boolean transformFlywayConstructor(Factory factory, CtNewClass<?> constructor, Map<String, String> methodTransforms) {
        // Get the parent statement to understand the context
        CtStatement parentStmt = constructor.getParent(CtStatement.class);
        if (parentStmt == null) {
            return false;
        }
        
        // Check if this is in a variable declaration
        String varName = null;
        if (parentStmt instanceof CtLocalVariable) {
            CtLocalVariable<?> localVar = (CtLocalVariable<?>) parentStmt;
            varName = localVar.getSimpleName();
        }
        
        // Create the Flyway.configure() expression
        CtTypeReference<?> flywayType = factory.createReference("org.flywaydb.core.Flyway");
        CtTypeReference<?> fluentConfigType = factory.createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
        
        // Build Flyway.configure()
        CtExecutableReference configureMethod = factory.createExecutableReference();
        configureMethod.setDeclaringType(flywayType);
        configureMethod.setSimpleName("configure");
        
        CtInvocation<?> configureInvocation = factory.createInvocation();
        configureInvocation.setExecutable(configureMethod);
        configureInvocation.setTarget(factory.createTypeAccess(flywayType));
        
        CtExpression<?> currentExpression = configureInvocation;
        
        // If we have a variable name, look for setters called on it
        if (varName != null) {
            CtBlock<?> containingBlock = constructor.getParent(CtBlock.class);
            if (containingBlock != null) {
                List<CtStatement> statements = containingBlock.getStatements();
                int constructorIdx = statements.indexOf(parentStmt);
                
                if (constructorIdx >= 0) {
                    // Look at statements after the constructor
                    for (int i = constructorIdx + 1; i < statements.size(); i++) {
                        CtStatement stmt = statements.get(i);
                        
                        // Find all invocations in this statement
                        List<CtInvocation<?>> invocations = stmt.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
                        for (CtInvocation<?> invoc : invocations) {
                            // Check if this invocation targets our variable
                            if (invoc.getTarget() != null && invoc.getTarget() instanceof CtVariableAccess) {
                                CtVariableAccess<?> target = (CtVariableAccess<?>) invoc.getTarget();
                                if (varName.equals(target.getVariable().getSimpleName())) {
                                    String methodName = invoc.getExecutable().getSimpleName();
                                    if (methodTransforms.containsKey(methodName)) {
                                        String newMethodName = methodTransforms.get(methodName);
                                        
                                        // Create the builder method call
                                        CtExecutableReference builderMethod = factory.createExecutableReference();
                                        builderMethod.setDeclaringType(fluentConfigType);
                                        builderMethod.setSimpleName(newMethodName);
                                        
                                        CtInvocation<?> builderInvocation = factory.createInvocation();
                                        builderInvocation.setExecutable(builderMethod);
                                        builderInvocation.setTarget(currentExpression);
                                        builderInvocation.setArguments(invoc.getArguments());
                                        
                                        currentExpression = builderInvocation;
                                        
                                        // Delete the old setter call
                                        invoc.delete();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Add .load() at the end
        CtExecutableReference loadMethod = factory.createExecutableReference();
        loadMethod.setDeclaringType(fluentConfigType);
        loadMethod.setSimpleName("load");
        
        CtInvocation<?> loadInvocation = factory.createInvocation();
        loadInvocation.setExecutable(loadMethod);
        loadInvocation.setTarget(currentExpression);
        
        // Replace the original constructor with our new expression
        constructor.replace(loadInvocation);
        
        return true;
    }
}