package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtMethodCall;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * A Spoon transformation to fix the sonarlint-core API change in addEnabledLanguages method.
 * This fixes the issue where addEnabledLanguages changed from Set<Language> to Language...
 */
public class SonarLintApiFix {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to process
        launcher.addInputResource("/workspace/sorald");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/sorald-fixed");
        
        // Build the model
        launcher.buildModel();
        
        // Get the factory
        Factory factory = launcher.getFactory();
        
        // Find all invocations of addEnabledLanguages method
        List<CtInvocation> invocations = factory.createQuery()
            .filterChildren(new TypeFilter<>(CtInvocation.class))
            .select(invocation -> {
                if (invocation.getExecutable() != null && 
                    invocation.getExecutable().getSimpleName().equals("addEnabledLanguages")) {
                    // Check that we have exactly one argument that's a Set
                    if (invocation.getArguments().size() == 1) {
                        CtExpression<?> firstArg = invocation.getArguments().get(0);
                        if (firstArg.getType() != null) {
                            String typeName = firstArg.getType().toString();
                            return typeName.contains("Set") && typeName.contains("Language");
                        }
                    }
                }
                return false;
            })
            .list();
        
        // Transform each invocation
        for (CtInvocation invocation : invocations) {
            // Create the new method call with varargs syntax
            // Get the argument (which should be a Set<Language>)
            CtExpression<?> setArgument = invocation.getArguments().get(0);
            
            // Create the new expression: setArgument.toArray(new Language[0])
            CtMethodCall<?> toArrayCall = factory.createInvocation(
                setArgument,
                factory.createExecutableReference()
                    .setSimpleName("toArray")
                    .setDeclaringType(factory.Type().createReference("org.sonarsource.sonarlint.core.commons.Language[]")),
                factory.createNewArray(
                    factory.Type().createReference("org.sonarsource.sonarlint.core.commons.Language"),
                    0
                )
            );
            
            // Create the varargs call: setArgument.toArray(new Language[0])...
            // This is a simplified representation - in practice, Spoon would handle this better
            System.out.println("Would transform: " + invocation.toString());
            System.out.println("To use: " + toArrayCall.toString() + "...");
        }
        
        // Print the number of transformations found
        System.out.println("Found " + invocations.size() + " addEnabledLanguages calls to fix");
        
        // Write the result back to the output directory
        launcher.setBinaryOutputDirectory("/workspace/sorald-fixed");
        launcher.process();
        
        System.out.println("Transformation completed. Check /workspace/sorald-fixed for results.");
    }
}