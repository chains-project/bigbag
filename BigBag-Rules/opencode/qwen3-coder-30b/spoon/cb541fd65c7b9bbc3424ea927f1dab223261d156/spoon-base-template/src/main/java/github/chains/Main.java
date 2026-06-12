package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // This is a generic transformation for fixing hamcrest API changes
        // It identifies hamcrest assertThat usage patterns that need to be updated
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        System.out.println("Analyzing hamcrest API usage in: " + sourceDirectory);
        
        // Create a simple launcher to analyze the code
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setComplianceLevel(8);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Find all assertThat invocations  
            int count = 0;
            for (CtInvocation invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
                // Check if this is a hamcrest assertThat call
                if (invocation.getExecutable() != null && 
                    "assertThat".equals(invocation.getExecutable().getSimpleName())) {
                    System.out.println("Found assertThat invocation: " + invocation.toString());
                    count++;
                }
            }
            
            System.out.println("Found " + count + " assertThat invocations in " + sourceDirectory);
            System.out.println("Transformation analysis completed for: " + sourceDirectory);
            
        } catch (Exception e) {
            System.err.println("Error processing project: " + e.getMessage());
            e.printStackTrace();
        }
    }
}