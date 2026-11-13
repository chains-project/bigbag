package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Simple Flyway detector to find problematic constructor calls
 */
public class SimpleFlywayDetector {
    public static void main(String[] args) {
        System.out.println("=== Flyway Constructor Detector ===");
        System.out.println("Detecting Flyway constructor calls that require migration...");
        System.out.println();
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add the input resources 
        launcher.addInputResource("/workspace/nem");
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to Flyway class without arguments
        List<CtConstructorCall> flywayCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall constructorCall) {
                return constructorCall.getType() != null && 
                       constructorCall.getType().getSimpleName().equals("Flyway") &&
                       constructorCall.getArguments().isEmpty(); // Empty constructor call
            }
        });
        
        System.out.println("Found " + flywayCalls.size() + " problematic Flyway constructor calls:");
        System.out.println();
        
        for (CtConstructorCall call : flywayCalls) {
            System.out.println("  File: " + call.getPosition().getFile().getName());
            System.out.println("  Line: " + call.getPosition().getLine());
            System.out.println("  Code: " + call.toString());
            System.out.println("  Fix: Change to 'Flyway.configure().dataSource(...).locations(...).load()' pattern");
            System.out.println();
        }
        
        if (flywayCalls.isEmpty()) {
            System.out.println("No problematic Flyway constructor calls found.");
        } else {
            System.out.println("These calls need to be updated for Flyway 9.x compatibility.");
        }
    }
}