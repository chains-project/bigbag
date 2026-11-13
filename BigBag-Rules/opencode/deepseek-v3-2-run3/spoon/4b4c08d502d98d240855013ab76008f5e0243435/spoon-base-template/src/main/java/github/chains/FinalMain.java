package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.CtScanner;

import java.util.List;
import java.util.ArrayList;

/**
 * Spoon transformation to migrate Flyway code from old API to new API:
 * 1. Find all `new Flyway()` constructor calls with no arguments
 * 2. Replace them with `new Flyway(config)` where config is `new ClassicConfiguration()`
 * 3. Move all `flyway.setXxx()` calls to `config.setXxx()` calls
 * 
 * Limitations:
 * - Method chaining (flyway.setX().setY()) is not fully supported
 * - Only transforms setter methods (methods starting with "set")
 * - Assumes setters are called directly on the Flyway variable
 */
public class FinalMain {
    
    // Helper scanner to collect setter invocations on a specific variable
    private static class SetterCollector extends CtScanner {
        private final String targetVarName;
        private final List<CtInvocation<?>> invocations;
        
        SetterCollector(String targetVarName) {
            this.targetVarName = targetVarName;
            this.invocations = new ArrayList<>();
        }
        
        @Override
        public <T> void visitCtInvocation(CtInvocation<T> invocation) {
            super.visitCtInvocation(invocation);
            
            // Check if this is a setter method
            String methodName = invocation.getExecutable().getSimpleName();
            if (!methodName.startsWith("set") || invocation.getArguments().size() == 0) {
                return;
            }
            
            // Check if the target is the Flyway variable
            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return;
            }
            
            // Check if target is directly the Flyway variable
            if (target instanceof CtVariableAccess) {
                CtVariableAccess<?> varAccess = (CtVariableAccess<?>) target;
                if (targetVarName.equals(varAccess.getVariable().getSimpleName())) {
                    invocations.add(invocation);
                }
            }
        }
        
        public List<CtInvocation<?>> getInvocations() {
            return invocations;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Create processor to transform Flyway constructor calls
        AbstractProcessor<CtConstructorCall<?>> processor = new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> constructorCall) {
                // Check if this is a constructor call to org.flywaydb.core.Flyway
                CtExecutableReference<?> execRef = constructorCall.getExecutable();
                if (execRef == null || execRef.getDeclaringType() == null) {
                    return;
                }
                
                String declaringType = execRef.getDeclaringType().getQualifiedName();
                if (!"org.flywaydb.core.Flyway".equals(declaringType)) {
                    return;
                }
                
                // Check if it's a no-arg constructor
                if (constructorCall.getArguments().size() != 0) {
                    return;
                }
                
                System.out.println("Found Flyway no-arg constructor at: " + constructorCall.getPosition());
                
                // Get the parent block to insert configuration variable before constructor call
                CtBlock<?> parentBlock = constructorCall.getParent(CtBlock.class);
                if (parentBlock == null) {
                    System.out.println("Warning: Could not find parent block for constructor at " + constructorCall.getPosition());
                    return;
                }
                
                // Create ClassicConfiguration type reference
                CtTypeReference<?> classicConfigType = factory.createReference("org.flywaydb.core.api.configuration.ClassicConfiguration");
                
                // Create unique variable name
                String configVarName = "flywayConfig";
                int counter = 1;
                while (true) {
                    boolean nameExists = false;
                    for (CtStatement stmt : parentBlock.getStatements()) {
                        if (stmt instanceof CtLocalVariable) {
                            CtLocalVariable<?> localVar = (CtLocalVariable<?>) stmt;
                            if (configVarName.equals(localVar.getSimpleName())) {
                                nameExists = true;
                                break;
                            }
                        }
                    }
                    if (!nameExists) {
                        break;
                    }
                    configVarName = "flywayConfig" + (counter++);
                }
                
                // Create new ClassicConfiguration constructor call
                @SuppressWarnings("unchecked")
                CtConstructorCall<Object> configConstructor = factory.createConstructorCall(
                    (CtTypeReference<Object>) classicConfigType
                );
                
                // Create local variable for configuration
                @SuppressWarnings("unchecked")
                CtLocalVariable<Object> configVar = factory.createLocalVariable(
                    (CtTypeReference<Object>) classicConfigType, 
                    configVarName, 
                    configConstructor
                );
                
                // Insert configuration variable declaration before the Flyway constructor call
                // Find the statement containing the constructor call
                CtStatement containingStatement = constructorCall.getParent(CtStatement.class);
                if (containingStatement != null) {
                    int constructorIndex = parentBlock.getStatements().indexOf(containingStatement);
                    if (constructorIndex >= 0) {
                        parentBlock.addStatement(constructorIndex, configVar);
                    } else {
                        // Fallback: add at beginning
                        parentBlock.addStatement(0, configVar);
                    }
                } else {
                    System.out.println("Warning: Could not find statement containing constructor at " + constructorCall.getPosition());
                    return;
                }
                
                // Get the Flyway variable name from the local variable declaration
                CtLocalVariable<?> flywayVarDecl = constructorCall.getParent(CtLocalVariable.class);
                if (flywayVarDecl == null) {
                    System.out.println("Warning: Could not find Flyway variable declaration");
                    return;
                }
                
                String flywayVarName = flywayVarDecl.getSimpleName();
                
                // Find all invocations on this flyway variable in the same block
                SetterCollector collector = new SetterCollector(flywayVarName);
                parentBlock.accept(collector);
                List<CtInvocation<?>> invocationsToTransform = collector.getInvocations();
                
                // Transform each setter invocation
                for (CtInvocation<?> invocation : invocationsToTransform) {
                    String methodName = invocation.getExecutable().getSimpleName();
                    List<CtExpression<?>> arguments = invocation.getArguments();
                    
                    // Create executable reference for the method on config
                    CtExecutableReference<?> configMethodRef = factory.createExecutableReference();
                    configMethodRef.setSimpleName(methodName);
                    configMethodRef.setDeclaringType(classicConfigType);
                    
                    // Create variable access for config
                    @SuppressWarnings("unchecked")
                    CtVariableAccess<Object> configVarAccess = factory.createVariableRead(
                        factory.createLocalVariableReference((CtTypeReference<Object>) classicConfigType, configVarName),
                        false
                    );
                    
                    @SuppressWarnings("unchecked")
                    CtInvocation<?> newInvocation = factory.createInvocation(
                        configVarAccess,
                        configMethodRef,
                        arguments.toArray(new CtExpression[0])
                    );
                    
                    // Replace the old invocation with the new one
                    invocation.replace(newInvocation);
                    System.out.println("Transformed setter call: " + methodName);
                }
                
                // Finally, modify the Flyway constructor to take the configuration
                List<CtExpression<?>> newArgs = new ArrayList<>();
                @SuppressWarnings("unchecked")
                CtVariableAccess<Object> configVarAccess = factory.createVariableRead(
                    factory.createLocalVariableReference((CtTypeReference<Object>) classicConfigType, configVarName),
                    false
                );
                newArgs.add(configVarAccess);
                
                constructorCall.setArguments(newArgs);
                
                System.out.println("Transformed Flyway constructor at: " + constructorCall.getPosition());
            }
        };
        
        // Add the processor
        launcher.addProcessor(processor);
        
        // Run the transformation
        launcher.process();
        
        // Output the transformed code
        launcher.setSourceOutputDirectory("./transformed");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to ./transformed");
    }
}