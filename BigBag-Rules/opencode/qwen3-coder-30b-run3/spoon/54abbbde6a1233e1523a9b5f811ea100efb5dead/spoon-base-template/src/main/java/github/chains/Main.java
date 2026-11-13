package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;

/**
 * Generic transformation to replace com.jcabi.aspects.Tv constants with literal values.
 * This fixes compilation errors caused by the removal of Tv class in jcabi-aspects 0.25.1
 */
public class Main {
    public static void main(String[] args) {
        // Process the jcabi-ssh project
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Set input and output directories
        launcher.addInputResource("/workspace/jcabi-ssh/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/jcabi-ssh/src/main/java-fixed");
        
        // Add the processor
        launcher.addProcessor(new TvConstantReplacer());
        
        // Build and process
        launcher.buildModel();
        launcher.process();
        
        System.out.println("Transformation completed!");
        System.out.println("Fixed files are in /workspace/jcabi-ssh/src/main/java-fixed");
    }
    
    /**
     * Processor that replaces Tv constants with their literal values
     */
    public static class TvConstantReplacer extends AbstractProcessor<CtFieldRead> {
        @Override
        public void process(CtFieldRead fieldRead) {
            // Check if this is a reference to Tv constants
            if (fieldRead.getVariable() instanceof CtFieldReference) {
                CtFieldReference<?> fieldRef = (CtFieldReference<?>) fieldRead.getVariable();
                String fieldName = fieldRef.getSimpleName();
                String typeName = fieldRef.getDeclaringType().getQualifiedName();
                
                // Replace Tv constants with literals
                if ("com.jcabi.aspects.Tv".equals(typeName)) {
                    switch (fieldName) {
                        case "SEVEN":
                            fieldRead.replace(fieldRead.getFactory().Code().createLiteral(7));
                            break;
                        case "MILLION":
                            fieldRead.replace(fieldRead.getFactory().Code().createLiteral(1000000));
                            break;
                        case "TEN":
                            fieldRead.replace(fieldRead.getFactory().Code().createLiteral(10));
                            break;
                        default:
                            // Ignore other Tv constants that we don't handle
                            break;
                    }
                }
            }
        }
    }
}