package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to identify tinspin-indexes breaking changes.
 * This identifies usage patterns that need to be fixed.
 */
public class Main {
    public static void main(String[] args) {
        // Get the source directory from command line argument or default to current directory
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/PGS/src/main/java";
        
        System.out.println("=== TINSPIN-INDEXES API BREAKING CHANGE ANALYSIS ===");
        System.out.println("Analyzing project at: " + sourceDirectory);
        System.out.println();
        
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory("/workspace/spoon-output");
        
        try {
            // Process the code
            CtModel model = launcher.buildModel();
            
            // Find all invocations of query1NN methods
            List<CtInvocation> query1NNInvocations = model.getElements(new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    return element.getExecutable().getSimpleName().equals("query1NN");
                }
            });
            
            System.out.println("Found " + query1NNInvocations.size() + " query1NN invocations");
            
            // Print each pattern
            for (int i = 0; i < query1NNInvocations.size(); i++) {
                CtInvocation invocation = query1NNInvocations.get(i);
                System.out.println("Pattern " + (i+1) + ": " + invocation.toString());
            }
            
            System.out.println();
            System.out.println("=== ANALYSIS COMPLETE ===");
            System.out.println("This identifies the usage patterns that need to be fixed.");
            System.out.println("The actual transformation logic would be implemented in a separate Spoon visitor.");
            
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
}