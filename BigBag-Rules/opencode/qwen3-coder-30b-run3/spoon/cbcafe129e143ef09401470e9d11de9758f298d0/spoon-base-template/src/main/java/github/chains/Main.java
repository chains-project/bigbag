package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic transformation to fix breaking dependency updates affecting logging APIs.
 * Specifically addresses issues with ch.qos.logback.classic.Logger references.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        
        CtModel model = launcher.buildModel();
        
        // Replace ch.qos.logback.classic.Logger references with org.slf4j.Logger  
        model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return element.getQualifiedName().equals("ch.qos.logback.classic.Logger");
            }
        }).forEach(ref -> {
            // Replace with org.slf4j.Logger
            CtTypeReference<?> newRef = launcher.getFactory().Type().createReference("org.slf4j.Logger");
            ref.replace(newRef);
        });

        // Generate the fixed files
        launcher.process();
        
        System.out.println("Transformation complete. Fixed files are in " + sourceDirectory + "_fixed");
    }
}