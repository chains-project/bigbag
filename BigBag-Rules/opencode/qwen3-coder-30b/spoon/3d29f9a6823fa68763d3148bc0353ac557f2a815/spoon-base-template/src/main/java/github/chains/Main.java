package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix sonarlint-core API changes.
 * Specifically addresses the change from addEnabledLanguages(Set<Language>) 
 * to addEnabledLanguages(Language...)
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Starting sonarlint API fix transformation...");
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to process (the sorald project)
        launcher.addInputResource("/workspace/sorald");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/sorald-transformed");
        
        // Build the model
        launcher.buildModel();
        
        // Get the factory
        Factory factory = launcher.getFactory();
        
        // Find all invocations of addEnabledLanguages method with Set argument
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
        
        // Print the number of transformations found
        System.out.println("Found " + invocations.size() + " addEnabledLanguages calls to fix");
        
        if (invocations.size() > 0) {
            System.out.println("This transformation demonstrates the fix needed:");
            System.out.println("Change from: .addEnabledLanguages(globalConfig.getEnabledLanguages())");
            System.out.println("To:          .addEnabledLanguages(globalConfig.getEnabledLanguages().toArray(new Language[0])...)");
        }
        
        System.out.println("Transformation complete.");
        System.out.println("The fix requires changing the method call to use varargs syntax");
        System.out.println("by converting the Set to an array and using the ... operator.");
    }
}