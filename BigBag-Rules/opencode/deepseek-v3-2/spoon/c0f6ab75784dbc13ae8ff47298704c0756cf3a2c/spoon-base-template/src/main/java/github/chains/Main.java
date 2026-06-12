package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking API changes in sonarlint-core 8.19.0.72745.
 * 
 * This rule handles the removal of `addEnabledLanguages(Set<Language>)` method from
 * `AnalysisEngineConfiguration.Builder` class.
 * 
 * The transformation:
 * 1. Finds all method calls to `addEnabledLanguages` on `AnalysisEngineConfiguration.Builder`
 * 2. Removes those calls from method chains
 * 
 * This is a generic rule that can be applied to any project affected by this breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        // Create Spoon launcher with noClasspath mode to avoid dependency issues
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        boolean modified = false;
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : invocations) {
            String methodName = invocation.getExecutable().getSimpleName();
            
            // Pattern: AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)
            if (methodName.equals("addEnabledLanguages")) {
                // Check if target looks like AnalysisEngineConfiguration.builder()
                String targetStr = invocation.getTarget() != null ? invocation.getTarget().toString() : "";
                
                if (targetStr.contains("AnalysisEngineConfiguration") && 
                    (targetStr.contains("builder()") || targetStr.contains(".builder"))) {
                    
                    System.out.println("Found addEnabledLanguages on AnalysisEngineConfiguration.Builder at: " + 
                        invocation.getPosition().toString());
                    
                    // Get the parent statement (the entire builder chain)
                    CtStatement parentStatement = invocation.getParent(CtStatement.class);
                    if (parentStatement != null) {
                        String originalCode = parentStatement.toString();
                        
                        // Get the argument to remove
                        String argToRemove = "";
                        if (!invocation.getArguments().isEmpty()) {
                            argToRemove = invocation.getArguments().get(0).toString();
                        }
                        
                        // Create pattern to remove: .addEnabledLanguages(<arg>)
                        String patternToRemove = ".addEnabledLanguages(" + argToRemove + ")";
                        
                        // Remove the pattern from the code
                        String newCode = originalCode.replace(patternToRemove, "");
                        
                        // Handle potential formatting issues
                        newCode = newCode.replace("..", ".");
                        newCode = newCode.replace("\n.", "\n");
                        
                        // If the code changed, replace it
                        if (!newCode.equals(originalCode)) {
                            CtCodeSnippetStatement newStmt = factory.createCodeSnippetStatement(newCode);
                            parentStatement.replace(newStmt);
                            modified = true;
                            System.out.println("  Removed addEnabledLanguages call.");
                        }
                    }
                }
            }
        }
        
        if (modified) {
            // Output the transformed code
            launcher.setSourceOutputDirectory("./transformed-output");
            launcher.prettyprint();
            System.out.println("\nTransformation complete!");
            System.out.println("Transformed files written to: ./transformed-output");
            System.out.println("\nSummary of changes:");
            System.out.println("- Removed calls to AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)");
            System.out.println("- This method was removed in sonarlint-core 8.19.0.72745");
        } else {
            System.out.println("No addEnabledLanguages calls found. No transformations applied.");
        }
    }
}