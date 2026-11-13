package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Hamcrest StringContains and StringStartsWith constructor signatures.
 * 
 * This transformation addresses breaking changes in Hamcrest where the constructors:
 * - StringContains(boolean, String) -> StringContains(String)
 * - StringStartsWith(boolean, String) -> StringStartsWith(String)
 * 
 * The transformation identifies all constructor calls to these classes and removes the 
 * first boolean parameter from the constructor call.
 */
public class Main {
    public static void main(String[] args) {
        // Configure the launcher with the appropriate source directory
        String sourceDir = System.getProperty("source.dir", "/workspace/docker-adapter");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory("/tmp/spoon-generated");
        
        CtModel model = launcher.buildModel();
        
        // Find all StringContains constructor calls
        List<CtConstructorCall> stringContainsCalls = model.getElements(
            new TypeFilter<>(CtConstructorCall.class)
        ).stream()
            .filter(call -> {
                CtTypeReference<?> typeRef = call.getType();
                return typeRef != null && 
                       ("org.hamcrest.core.StringContains".equals(typeRef.getQualifiedName()) ||
                        "org.hamcrest.StringContains".equals(typeRef.getQualifiedName()));
            })
            .toList();

        // Find all StringStartsWith constructor calls
        List<CtConstructorCall> stringStartsWithCalls = model.getElements(
            new TypeFilter<>(CtConstructorCall.class)
        ).stream()
            .filter(call -> {
                CtTypeReference<?> typeRef = call.getType();
                return typeRef != null && 
                       ("org.hamcrest.core.StringStartsWith".equals(typeRef.getQualifiedName()) ||
                        "org.hamcrest.StringStartsWith".equals(typeRef.getQualifiedName()));
            })
            .toList();

        // Transform StringContains calls
        for (CtConstructorCall call : stringContainsCalls) {
            fixHamcrestConstructorCall(call, "StringContains");
        }

        // Transform StringStartsWith calls
        for (CtConstructorCall call : stringStartsWithCalls) {
            fixHamcrestConstructorCall(call, "StringStartsWith");
        }

        // Print how many transformations were applied
        System.out.println("Fixed " + stringContainsCalls.size() + " StringContains constructor calls");
        System.out.println("Fixed " + stringStartsWithCalls.size() + " StringStartsWith constructor calls");

        // Write the modified code back to disk
        launcher.setSourceOutputDirectory(sourceDir + "/transformed");
        launcher.setSourceOutputDirectory(sourceDir + "/transformed");
        launcher.process();
    }

    /**
     * Fix a Hamcrest constructor call by removing the first boolean parameter if it exists.
     */
    private static void fixHamcrestConstructorCall(CtConstructorCall call, String className) {
        // Check if the constructor takes 2 arguments (boolean, String)
        if (call.getArguments().size() == 2) {
            Object firstArg = call.getArguments().get(0);
            if (firstArg instanceof spoon.reflect.code.CtLiteral) {
                spoon.reflect.code.CtLiteral literal = (spoon.reflect.code.CtLiteral) firstArg;
                if ("true".equals(literal.getValue()) || "false".equals(literal.getValue())) {
                    // Remove the first argument as it's the boolean parameter we want to remove
                    call.getArguments().remove(0);
                    System.out.println("Fixed " + className + " constructor call");
                }
            }
        }
    }
}