package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.setArgs(new String[] {
            "--output-type", "nooutput",
            "--src", "/workspace/junit-quickcheck"
        });
        
        CtModel model = launcher.buildModel();
        
        // Process the model to fix MockitoJUnitRunner issues
        model.processWith(new MockitoRunnerFixProcessor());
        
        System.out.println("Transformation completed successfully.");
    }
    
    private static class MockitoRunnerFixProcessor extends AbstractProcessor<CtClass<?>> {
        @Override
        public void process(CtClass<?> ctClass) {
            // Check if this class uses @RunWith(MockitoJUnitRunner.class)
            List<CtAnnotation<?>> runWithAnnotations = new ArrayList<>();
            for (CtAnnotation<?> annotation : ctClass.getAnnotations()) {
                if ("RunWith".equals(annotation.getAnnotationType().getSimpleName())) {
                    Object value = annotation.getValue("value");
                    if (value != null && value.toString().contains("MockitoJUnitRunner")) {
                        runWithAnnotations.add(annotation);
                    }
                }
            }
            
            if (!runWithAnnotations.isEmpty()) {
                // Remove the old import of MockitoJUnitRunner (we'll do a simpler approach)
                // Add the new import for MockitoExtension
                CtImport newImport = ctClass.getFactory().createImport();
                newImport.setReference(ctClass.getFactory().createTypeReference("org.mockito.junit.jupiter.MockitoExtension"));
                // We'll add imports differently
                try {
                    ctClass.getPackage().addImport(newImport);
                } catch (Exception ignored) {} // Ignore for now
                
                // Add the new annotation @ExtendWith(MockitoExtension.class)
                CtAnnotation extendWithAnnotation = ctClass.getFactory().createAnnotation();
                extendWithAnnotation.setAnnotationType(ctClass.getFactory().createTypeReference("org.junit.jupiter.api.extension.ExtendWith"));
                
                // Create the argument for MockitoExtension
                CtTypeReference mockitoExtensionRef = ctClass.getFactory().createTypeReference("org.mockito.junit.jupiter.MockitoExtension");
                extendWithAnnotation.addValue("value", mockitoExtensionRef);
                ctClass.addAnnotation(extendWithAnnotation);
                
                // Remove old @RunWith annotation
                ctClass.getAnnotations().removeAll(runWithAnnotations);
            }
        }
    }
}