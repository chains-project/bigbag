package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.code.*;
import spoon.reflect.reference.*;
import spoon.support.visitor.ProcessingVisitor;
import spoon.processing.AbstractProcessor;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <project-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project");
            System.exit(1);
        }
        
        String projectPath = args[0];
        System.out.println("Processing project: " + projectPath);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(projectPath);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Create and add processor
        launcher.addProcessor(new MapStructMapperProcessor());
        
        // Process the model
        launcher.process();
        
        // Write the transformed code
        launcher.setSourceOutputDirectory(projectPath);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Processor that fixes MapStruct @Mapper annotations by adding componentModel attribute.
     * This handles the breaking change from MapStruct 1.4.x to 1.5.x where 
     * componentModel attribute may become required.
     */
    static class MapStructMapperProcessor extends AbstractProcessor<CtType<?>> {
        
        // Fully qualified name of the MapStruct Mapper annotation
        private static final String MAPSTRUCT_MAPPER_ANNOTATION = "org.mapstruct.Mapper";
        
        // Target component model value - can be parameterized
        private static final String TARGET_COMPONENT_MODEL = "\"spring\"";
        
        @Override
        public void process(CtType<?> type) {
// Check if the type has the @Mapper annotation
        CtAnnotation<?> mapperAnnotation = null;
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            if (annotation.getAnnotationType().getQualifiedName().equals(MAPSTRUCT_MAPPER_ANNOTATION)) {
                mapperAnnotation = annotation;
                break;
            }
        }
        
        if (mapperAnnotation != null) {
            System.out.println("Found @Mapper annotation on: " + type.getQualifiedName());
            
            // Check if componentModel attribute is already present
            boolean hasComponentModel = mapperAnnotation.getValues().keySet().stream()
                .anyMatch(key -> key.toString().equals("componentModel"));
            
            if (!hasComponentModel) {
                System.out.println("  -> Adding componentModel = " + TARGET_COMPONENT_MODEL);
                
                // Remove the old annotation
                type.removeAnnotation(mapperAnnotation);
                
                // Create the new annotation with componentModel attribute
                CtAnnotation<?> newAnnotation = getFactory().createAnnotation(
                    getFactory().Annotation().createReference(MAPSTRUCT_MAPPER_ANNOTATION)
                );
                
                // Add componentModel attribute
                CtExpression<String> componentModelValue = getFactory().createCodeSnippetExpression(TARGET_COMPONENT_MODEL);
                newAnnotation.addValue("componentModel", componentModelValue);
                
                // Add the new annotation
                type.addAnnotation(newAnnotation);
            } else {
                System.out.println("  -> Already has componentModel attribute, skipping");
            }
        }
        }
        
        @Override
        public boolean isToBeProcessed(CtType<?> candidate) {
            // Process all types (interfaces and classes) that have @Mapper annotation
            for (CtAnnotation<?> annotation : candidate.getAnnotations()) {
                if (annotation.getAnnotationType().getQualifiedName().equals(MAPSTRUCT_MAPPER_ANNOTATION)) {
                    return true;
                }
            }
            return false;
        }
    }
}