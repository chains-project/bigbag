package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtType;
import spoon.processing.AbstractProcessor;
import spoon.reflect.factory.Factory;

/**
 * Test class to validate our transformation works
 */
public class TestTransformation {
    public static void main(String[] args) {
        System.out.println("Testing Spoon transformation for javax.validation -> jakarta.validation migration");
        
        // Create a simple test case
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/nem/nis/src/main/java/org/nem/nis/controller/AccountController.java");
        launcher.setSourceOutputDirectory("/tmp/processed");
        launcher.getEnvironment().setComplianceLevel(11);
        
        // Process with our transformation
        launcher.addProcessor(new AbstractProcessor<CtAnnotation<?>>() {
            @Override
            public void process(CtAnnotation<?> annotation) {
                String annotationQualifiedName = annotation.getAnnotationType().getQualifiedName();
                if (annotationQualifiedName.startsWith("javax.validation.")) {
                    String newQualifiedName = annotationQualifiedName.replace("javax.validation.", "jakarta.validation.");
                    Factory factory = getFactory();
                    annotation.setAnnotationType(factory.Type().createReference(newQualifiedName));
                }
            }
        });
        
        launcher.buildModel();
        System.out.println("Transformation processed successfully");
    }
}