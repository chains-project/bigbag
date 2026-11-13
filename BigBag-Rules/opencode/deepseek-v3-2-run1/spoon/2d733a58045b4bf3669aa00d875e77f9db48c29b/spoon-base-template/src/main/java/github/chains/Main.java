package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        // Process Java files in the project
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false); // Use classpath to preserve imports
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        
        try {
            launcher.run();
            
            Factory factory = launcher.getFactory();
            CtPackage rootPackage = factory.Package().getRootPackage();
            
            // Find all constructor calls to org.flywaydb.core.Flyway
            List<CtConstructorCall<?>> flywayCtorCalls = rootPackage.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    CtTypeReference<?> typeRef = element.getType();
                    return typeRef != null && 
                           "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName()) &&
                           element.getArguments().isEmpty(); // No-arg constructor
                }
            });
            
            System.out.println("Found " + flywayCtorCalls.size() + " Flyway constructor calls to transform");
            
            // Process each Flyway constructor call
            for (CtConstructorCall<?> ctorCall : flywayCtorCalls) {
                transformFlywayConstruction(ctorCall);
            }
            
// Output transformed code
        String outputDir = "/workspace/nem-transformed";
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
            
            System.out.println("Transformation complete. Output written to: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformFlywayConstruction(CtConstructorCall<?> ctorCall) {
        Factory factory = ctorCall.getFactory();
        
        // Get the parent statement (should be a variable assignment or declaration)
        CtStatement parentStatement = ctorCall.getParent(CtStatement.class);
        if (parentStatement == null) {
            System.err.println("Warning: Flyway constructor call not in a statement context");
            return;
        }
        
        // Check if this is part of a variable declaration or assignment
        CtLocalVariable<?> localVar = ctorCall.getParent(CtLocalVariable.class);
        CtAssignment<?, ?> assignment = ctorCall.getParent(CtAssignment.class);
        
        String varName = null;
        if (localVar != null) {
            varName = localVar.getSimpleName();
        } else if (assignment != null) {
            CtExpression<?> assignedVar = assignment.getAssigned();
            if (assignedVar instanceof CtVariableAccess) {
                varName = ((CtVariableAccess<?>) assignedVar).getVariable().getSimpleName();
            }
        }
        
        if (varName == null) {
            System.err.println("Warning: Could not determine variable name for Flyway instance");
            return;
        }
        
        // Get the containing block to find subsequent setter calls
        CtBlock<?> containingBlock = ctorCall.getParent(CtBlock.class);
        if (containingBlock == null) {
            System.err.println("Warning: Flyway constructor not in a block");
            return;
        }
        
        // Find all setter method calls on this variable
        List<CtInvocation<?>> setterCalls = new ArrayList<>();
        int ctorIndex = containingBlock.getStatements().indexOf(parentStatement);
        
        for (int i = ctorIndex + 1; i < containingBlock.getStatements().size(); i++) {
            CtStatement stmt = containingBlock.getStatements().get(i);
            if (stmt instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) stmt;
                CtExpression<?> target = invocation.getTarget();
                if (target instanceof CtVariableAccess) {
                    String targetVarName = ((CtVariableAccess<?>) target).getVariable().getSimpleName();
                    if (varName.equals(targetVarName) && invocation.getExecutable().getSimpleName().startsWith("set")) {
                        setterCalls.add(invocation);
                    } else {
                        // Not a setter on our variable, stop searching
                        break;
                    }
                } else {
                    // Not a variable access, stop searching
                    break;
                }
            } else {
                // Not an invocation, stop searching
                break;
            }
        }
        
        System.out.println("Found " + setterCalls.size() + " setter calls for variable: " + varName);
        
        // Create the new fluent API construction
        // Start with Flyway.configure()
        CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
        CtExecutableReference<?> configureMethod = factory.createExecutableReference();
        configureMethod.setDeclaringType(flywayType);
        configureMethod.setSimpleName("configure");
        configureMethod.setStatic(true);
        
        CtInvocation<?> configureCall = factory.createInvocation(
            factory.createTypeAccess(flywayType),
            configureMethod
        );
        
        // Check if we need to pass classLoader to configure()
        boolean hasClassLoaderSetter = false;
        CtExpression<?> classLoaderArg = null;
        
        for (CtInvocation<?> setter : setterCalls) {
            if ("setClassLoader".equals(setter.getExecutable().getSimpleName())) {
                hasClassLoaderSetter = true;
                if (!setter.getArguments().isEmpty()) {
                    classLoaderArg = setter.getArguments().get(0);
                }
                break;
            }
        }
        
        CtInvocation<?> fluentConfigCall;
        if (hasClassLoaderSetter && classLoaderArg != null) {
            // Use Flyway.configure(classLoader)
            CtExecutableReference<?> configureWithClassLoader = factory.createExecutableReference();
            configureWithClassLoader.setDeclaringType(flywayType);
            configureWithClassLoader.setSimpleName("configure");
            configureWithClassLoader.setStatic(true);
            
            fluentConfigCall = factory.createInvocation(
                factory.createTypeAccess(flywayType),
                configureWithClassLoader,
                classLoaderArg
            );
        } else {
            fluentConfigCall = configureCall;
        }
        
        // Map setter names to fluent API method names
        Map<String, String> setterToFluentMap = new HashMap<>();
        setterToFluentMap.put("setDataSource", "dataSource");
        setterToFluentMap.put("setLocations", "locations");
        setterToFluentMap.put("setValidateOnMigrate", "validateOnMigrate");
        // Add more mappings as needed
        
        // Apply fluent method calls for each setter
        CtInvocation<?> currentChain = fluentConfigCall;
        
        for (CtInvocation<?> setter : setterCalls) {
            String setterName = setter.getExecutable().getSimpleName();
            String fluentMethodName = setterToFluentMap.get(setterName);
            
            if (fluentMethodName != null && !"setClassLoader".equals(setterName)) {
                CtExecutableReference<?> fluentMethod = factory.createExecutableReference();
                fluentMethod.setDeclaringType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
                fluentMethod.setSimpleName(fluentMethodName);
                
                List<CtExpression<?>> args = new ArrayList<>(setter.getArguments());
                currentChain = factory.createInvocation(currentChain, fluentMethod, args);
            }
        }
        
        // Add .load() at the end
        CtExecutableReference<?> loadMethod = factory.createExecutableReference();
        loadMethod.setDeclaringType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
        loadMethod.setSimpleName("load");
        
        CtInvocation<?> finalCall = factory.createInvocation(currentChain, loadMethod);
        
        // Replace the constructor call with the new fluent API call
        ctorCall.replace(finalCall);
        
        // Remove the setter call statements
        for (CtInvocation<?> setter : setterCalls) {
            try {
                containingBlock.removeStatement(setter);
            } catch (Exception e) {
                System.err.println("Warning: Could not remove setter call: " + setter);
            }
        }
        
        System.out.println("Transformed Flyway construction for variable: " + varName);
    }
}