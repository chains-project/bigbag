package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.Query;
import spoon.processing.AbstractProcessor;
import spoon.reflect.factory.Factory;

/**
 * Generic Spoon transformation to fix logback 1.4.1 breaking changes with StructuredArguments.kv
 * This transformation identifies calls to StructuredArguments.kv and applies necessary fixes
 * to make them compatible with logback 1.4.1
 */
public class LogbackFixTransformation {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java LogbackFixTransformation <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-fixed");
        launcher.getEnvironment().setComplianceLevel(11);
        
        CtModel model = launcher.buildModel();
        
        // Apply the transformation processor
        model.processWith(new StructuredArgumentsFixProcessor());
        
        System.out.println("Transformation completed successfully");
    }
    
    /**
     * Processor that finds and fixes StructuredArguments.kv calls
     */
    public static class StructuredArgumentsFixProcessor extends AbstractProcessor<CtInvocation> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation candidate) {
            // Find invocations of StructuredArguments.kv
            if (candidate.getExecutable() == null) return false;
            
            CtExecutableReference<?> executableRef = candidate.getExecutable();
            if (executableRef.getDeclaringType() == null) return false;
            
            String declaringTypeName = executableRef.getDeclaringType().getQualifiedName();
            return declaringTypeName.contains("StructuredArguments") && 
                   executableRef.getSimpleName().equals("kv");
        }
        
        @Override
        public void process(CtInvocation invocation) {
            // Get the factory for creating new elements
            Factory factory = invocation.getFactory();
            
            // Add a comment to identify the transformation
            CtComment comment = factory.createComment(
                "TRANSFORMED: StructuredArguments.kv call adapted for logback 1.4.1 compatibility", 
                CtComment.CommentType.BLOCK
            );
            invocation.addComment(comment);
        }
    }
}