package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;

/**
 * Generic transformation to fix SnakeYAML 2.0 breaking change in Constructor API
 * 
 * The problem: In SnakeYAML 1.x, Constructor could be instantiated with just a Class parameter
 * In SnakeYAML 2.0, Constructor requires a LoaderOptions parameter
 * 
 * This transformation finds all Constructor calls with a single Class argument and adds
 * a new LoaderOptions() parameter to match the new API.
 */
public class Main {
    
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/simplelocalize-cli/src/main/java");
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setSourceOutputDirectory("/workspace/simplelocalize-cli/src-fixed");
        
        // Add our processor
        launcher.addProcessor(new AbstractProcessor<CtElement>() {
            @Override
            public void process(CtElement element) {
                if (element instanceof CtConstructorCall) {
                    CtConstructorCall call = (CtConstructorCall) element;
                    CtTypeReference<?> ctType = call.getType();
                    if (ctType == null) return;
                    String typeName = ctType.getQualifiedName();
                    
                    // Match Constructor class from snakeyaml
                    if (typeName.equals("org.yaml.snakeyaml.constructor.Constructor")) {
                        // Check if it's called with a single Class argument
                        if (call.getArguments().size() == 1) {
                            // Create a new constructor call with LoaderOptions parameter
                            Factory factory = getFactory();
                            CtConstructorCall newCall = factory.Core().createConstructorCall();
                            newCall.setType(call.getType());
                            
                            // Copy the original arguments
                            newCall.getArguments().addAll(call.getArguments());
                            
                            // Add LoaderOptions parameter
                            CtConstructorCall loaderOptionsCall = factory.Core().createConstructorCall();
                            CtTypeReference<?> loaderOptionsType = factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions");
                            loaderOptionsCall.setType(loaderOptionsType);
                            newCall.getArguments().add(loaderOptionsCall);
                            
                            // Replace the old call with the new one
                            call.replace(newCall);
                        }
                    }
                }
            }
        });
        
        // Build the model
        launcher.buildModel();
        
        // Generate the output
        launcher.process();
        
        System.out.println("Transformation completed successfully!");
    }
}