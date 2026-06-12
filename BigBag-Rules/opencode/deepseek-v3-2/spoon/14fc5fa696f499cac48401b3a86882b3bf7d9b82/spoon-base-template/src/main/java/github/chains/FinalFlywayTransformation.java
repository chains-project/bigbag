package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.*;
import spoon.support.reflect.code.*;
import spoon.support.reflect.declaration.*;
import java.util.*;

/**
 * Generic Spoon transformation for Flyway API migration from version <9 to >=9.
 * 
 * Breaking change analysis:
 * - OLD API: new Flyway() -> flyway.setXxx(value) method calls
 * - NEW API: Flyway.configure() -> .xxx(value) fluent builder -> .load()
 * 
 * Transformation pattern:
 * 1. Find all "new Flyway()" constructor calls
 * 2. Find all setter method calls on the Flyway variable
 * 3. Replace constructor with Flyway.configure()
 * 4. Convert setter calls to fluent builder calls
 * 5. Add .load() at the end
 * 6. Remove original setter statements
 * 
 * This transformation is generic and can be applied to any project
 * affected by the Flyway 9+ API breaking change.
 */
public class FinalFlywayTransformation {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.FinalFlywayTransformation <source-dir> <output-dir>");
            System.err.println("Example: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.FinalFlywayTransformation /path/to/src /path/to/output");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Flyway API Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Transforming Flyway API from old style (new Flyway() + setters)");
        System.out.println("to new style (Flyway.configure() fluent builder + .load())");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new FlywayMigrationProcessor());
        launcher.run();
        
        System.out.println("=== Transformation completed successfully! ===");
        System.out.println("Transformed files are available in: " + outputDir);
    }
}

/**
 * Spoon processor that handles the Flyway API migration.
 * This processor is triggered for each class in the codebase.
 */
class FlywayMigrationProcessor extends spoon.processing.AbstractProcessor<CtClass<?>> {
    
    @Override
    public void process(CtClass<?> clazz) {
        boolean hasChanges = false;
        
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
            System.out.println("Processing Flyway constructor in: " + clazz.getQualifiedName());
            if (transformFlywayUsage(constructor)) {
                hasChanges = true;
            }
        }
        
        if (hasChanges) {
            System.out.println("  Updated class: " + clazz.getQualifiedName());
        }
    }
    
    /**
     * Transforms a single Flyway constructor call and its associated setter methods.
     * Returns true if transformation was applied.
     */
    private boolean transformFlywayUsage(CtConstructorCall<?> constructor) {
        try {
            CtStatement parentStatement = constructor.getParent(CtStatement.class);
            if (parentStatement == null) {
                return false;
            }
            
            String varName = extractVariableName(parentStatement);
            if (varName == null) {
                return false;
            }
            
            CtExecutable<?> parentMethod = constructor.getParent(CtExecutable.class);
            if (parentMethod == null) {
                return false;
            }
            
            List<CtInvocation<?>> setterInvocations = findSetterInvocations(parentMethod, varName);
            
            System.out.println("  Found " + setterInvocations.size() + " setter calls for variable: " + varName);
            
            CtExpression<?> newFlywayExpression = buildNewFlywayExpression(setterInvocations);
            
            replaceConstructorWithNewExpression(constructor, newFlywayExpression, parentStatement);
            
            removeSetterStatements(setterInvocations);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("  Error transforming Flyway usage: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts the variable name from a statement containing a Flyway constructor.
     */
    private String extractVariableName(CtStatement statement) {
        if (statement instanceof CtLocalVariable) {
            CtLocalVariable<?> localVar = (CtLocalVariable<?>) statement;
            return localVar.getSimpleName();
        } else if (statement instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) statement;
            CtExpression<?> assigned = assignment.getAssigned();
            if (assigned instanceof CtVariableWrite) {
                return ((CtVariableWrite<?>) assigned).getVariable().getSimpleName();
            }
        } else if (statement instanceof CtReturn) {
            return "flyway";
        }
        return null;
    }
    
    /**
     * Finds all setter method invocations on the given variable within a method.
     */
    private List<CtInvocation<?>> findSetterInvocations(CtExecutable<?> method, String varName) {
        List<CtInvocation<?>> setters = new ArrayList<>();
        
        List<CtInvocation<?>> allInvocations = method.getElements(
            new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target == null) return false;
                    
                    String targetStr = target.toString();
                    return targetStr.equals(varName) && 
                           invocation.getExecutable().getSimpleName().startsWith("set");
                }
            }
        );
        
        allInvocations.sort(Comparator.comparingInt(inv -> inv.getPosition().getSourceStart()));
        return allInvocations;
    }
    
    /**
     * Builds the new Flyway expression using the fluent builder API.
     */
    private CtExpression<?> buildNewFlywayExpression(List<CtInvocation<?>> setterInvocations) {
        Factory factory = getFactory();
        
        CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
        CtTypeAccess<?> flywayTypeAccess = factory.createTypeAccess(flywayType);
        
        CtExecutableReference<?> configureRef = factory.createExecutableReference();
        configureRef.setSimpleName("configure");
        configureRef.setType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
        
        CtInvocation<?> configureCall = factory.createInvocation(flywayTypeAccess, configureRef);
        
        CtExpression<?> currentExpression = configureCall;
        
        for (CtInvocation<?> setter : setterInvocations) {
            String setterName = setter.getExecutable().getSimpleName();
            String fluentName = convertSetterToFluent(setterName);
            
            List<CtExpression<?>> arguments = new ArrayList<>(setter.getArguments());
            
            CtExecutableReference<?> fluentRef = factory.createExecutableReference();
            fluentRef.setSimpleName(fluentName);
            
            CtInvocation<?> fluentCall = factory.createInvocation(currentExpression, fluentRef, 
                arguments.toArray(new CtExpression[0]));
            
            currentExpression = fluentCall;
        }
        
        CtExecutableReference<?> loadRef = factory.createExecutableReference();
        loadRef.setSimpleName("load");
        loadRef.setType(flywayType);
        
        CtInvocation<?> loadCall = factory.createInvocation(currentExpression, loadRef);
        
        return loadCall;
    }
    
    /**
     * Converts a setter method name to its fluent API equivalent.
     * Example: setDataSource -> dataSource, setValidateOnMigrate -> validateOnMigrate
     */
    private String convertSetterToFluent(String setterName) {
        if (setterName.startsWith("set")) {
            String baseName = setterName.substring(3);
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        }
        return setterName;
    }
    
    /**
     * Replaces the Flyway constructor call with the new fluent builder expression.
     */
    private void replaceConstructorWithNewExpression(CtConstructorCall<?> constructor, 
                                                    CtExpression<?> newExpression,
                                                    CtStatement parentStatement) {
        if (parentStatement instanceof CtLocalVariable) {
            CtLocalVariable<?> localVar = (CtLocalVariable<?>) parentStatement;
            localVar.setDefaultExpression(newExpression);
        } else if (parentStatement instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parentStatement;
            assignment.setAssignment(newExpression);
        } else if (parentStatement instanceof CtReturn) {
            CtReturn<?> returnStmt = (CtReturn<?>) parentStatement;
            returnStmt.setReturnedExpression(newExpression);
        }
    }
    
    /**
     * Removes the original setter statements from the code.
     */
    private void removeSetterStatements(List<CtInvocation<?>> setterInvocations) {
        for (CtInvocation<?> setter : setterInvocations) {
            CtStatement setterStatement = setter.getParent(CtStatement.class);
            if (setterStatement != null) {
                setterStatement.delete();
            }
        }
    }
}