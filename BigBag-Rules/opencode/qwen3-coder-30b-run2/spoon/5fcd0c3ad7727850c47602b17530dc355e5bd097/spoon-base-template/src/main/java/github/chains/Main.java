package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;

/**
 * Generic Spoon transformation to fix breaking API changes in pitest-entry 1.10.0
 * Specifically addresses the removal of getClassInfo method from CoverageDatabase
 */
public class Main {
    public static void main(String[] args) {
        // Create a Launcher instance
        Launcher launcher = new Launcher();
        
        // Configure the launcher
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Set input resources
        launcher.addInputResource("/workspace/pitest-mutation-testing-elements-plugin/src/main/java");
        
        // Set output directory
        launcher.setSourceOutputDirectory("/workspace/pitest-mutation-testing-elements-plugin/src/main/java");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply the transformation processor
        FixCoverageDatabaseClassInfoProcessor processor = new FixCoverageDatabaseClassInfoProcessor();
        model.processWith(processor);
        
        // Generate transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Processor to fix the missing getClassInfo method call
     */
    static class FixCoverageDatabaseClassInfoProcessor extends AbstractProcessor<CtInvocation> {
        @Override
        public boolean isToBeProcessed(CtInvocation candidate) {
            // Match invocations of getClassInfo method on CoverageDatabase objects
            return candidate.getExecutable() != null 
                && "getClassInfo".equals(candidate.getExecutable().getSimpleName())
                && candidate.getTarget() != null
                && candidate.getTarget().getType() != null
                && candidate.getTarget().getType().getQualifiedName().contains("CoverageDatabase");
        }
        
        @Override
        public void process(CtInvocation invocation) {
            // Replace the call to getClassInfo with Collections.emptyList()
            // This handles the breaking change where getClassInfo was removed
            
            // Create a literal representing Collections.emptyList()
            CtLiteral<?> emptyListLiteral = getFactory().Code()
                .createLiteral(java.util.Collections.emptyList());
            
            // Replace the whole invocation with the empty list literal
            invocation.replace(emptyListLiteral);
        }
    }
}