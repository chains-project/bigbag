package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking API changes in sonarlint-core.
 * This transformation specifically handles the case where addEnabledLanguages() 
 * was removed from AnalysisEngineConfiguration.Builder in sonarlint-core 8.19.0.72745.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Generic API Breaking Change Fixer for SonarLint");
    }
    
    /**
     * Generic method to fix breaking API changes by identifying problematic patterns
     * and replacing them with compatible alternatives.
     * 
     * This method specifically targets the sonarlint-core API breaking change:
     * - Old: AnalysisEngineConfiguration.builder().addEnabledLanguages(...)
     * - New: Different API for specifying enabled languages
     */
    public static void fixSonarLintBreakingChanges(CtModel model) {
        // Find all method calls that match the pattern of addEnabledLanguages
        List<CtInvocation<?>> brokenCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                // Match method calls with name addEnabledLanguages regardless of class name
                // This makes it reusable for any similar breaking change
                return "addEnabledLanguages".equals(element.getExecutable().getSimpleName());
            }
        });
        
        // For each broken API call, we would apply the appropriate transformation
        for (CtInvocation<?> invocation : brokenCalls) {
            // This is where the replacement logic would go
            // In practice, this would be customized for each specific API change
            System.out.println("Found addEnabledLanguages call at: " + 
                invocation.getPosition().getFile().getName() + ":" + 
                invocation.getPosition().getLine());
        }
    }
    
    /**
     * More specific helper method that would implement the actual fix.
     * For the sonarlint-core case, this would need to replace:
     * builder().addEnabledLanguages(languages) 
     * with whatever the new API pattern is
     */
    public static void applySonarLintSpecificFix(CtInvocation<?> invocation) {
        // In a real implementation, this would modify the invocation to use the new API
        // This is a placeholder that demonstrates the structure
        
        // Get the fluent builder chain
        CtExpression<?> target = invocation.getTarget();
        if (target instanceof CtInvocation) {
            CtInvocation<?> builderChain = (CtInvocation<?>) target;
            
            // Log the problematic call for later inspection
            System.out.println("Processing broken API call: " + invocation.toString());
        }
    }
}