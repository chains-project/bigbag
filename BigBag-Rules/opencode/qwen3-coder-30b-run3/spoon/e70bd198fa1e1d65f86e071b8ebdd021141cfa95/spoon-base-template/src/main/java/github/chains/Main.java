package github.chains;

import java.util.List;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.*;

/**
 * Generic Spoon transformation to fix breaking changes in tinspin-indexes 2.0.1
 * This transformation handles:
 * 1. Changes in query1NN method signatures for KDTree and CoverTree
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            return;
        }

        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        model.processWith(new Query1NNFixProcessor());
        
        launcher.process();
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Processor to fix query1NN method calls for KDTree and CoverTree
     */
    static class Query1NNFixProcessor extends AbstractProcessor<CtInvocation> {
        @Override
        public void process(CtInvocation element) {
            // Check if this is a call to query1NN
            if (element.getExecutable() != null && 
                "query1nn".equalsIgnoreCase(element.getExecutable().getSimpleName())) {
                
                CtTypeReference<?> targetType = element.getTarget() != null ? 
                    element.getTarget().getType() : null;
                
                if (targetType != null) {
                    String targetTypeName = targetType.toString();
                    
                    // Check if it's a KDTree or CoverTree invocation
                    if (targetTypeName.contains("KDTree") || targetTypeName.contains("CoverTree")) {
                        // Get the arguments
                        List<CtExpression<?>> arguments = element.getArguments();
                        
                        // If there's only one argument (double[]), we need to add a default distance parameter
                        if (arguments.size() == 1) {
                            CtExpression<?> arg = arguments.get(0);
                            if (arg.getType() != null && 
                                arg.getType().toString().equals("double[]")) {
                                
                                // Create a default PointDistance.L2 parameter using string literal
                                Factory factory = element.getFactory();
                                CtLiteral<String> l2Literal = factory.Code().createLiteral("org.tinspin.index.PointDistance.L2");
                                element.addArgument(l2Literal);
                            }
                        }
                    }
                }
            }
        }
    }
}