package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Launcher launcher = new Launcher();
        
        // Configure Spoon
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all annotations in the model
        List<CtAnnotation<?>> annotations = model.getElements(new TypeFilter<>(CtAnnotation.class));
        
        for (CtAnnotation<?> annotation : annotations) {
            // Check if this is a @Mapper annotation
            if (annotation.getAnnotationType() != null && 
                annotation.getAnnotationType().getSimpleName().equals("Mapper")) {
                
                // Check if annotation has no explicit values
                if (annotation.getValues().isEmpty()) {
                    System.out.println("Found @Mapper annotation without parameters at: " + 
                                      annotation.getPosition().toString());
                    
                    // In a real transformation, we would modify the annotation here
                    // Example: annotation.addValue("componentModel", "default")
                }
            }
        }
        
        System.out.println("\n=== Generic Transformation Rule Summary ===");
        System.out.println("Breaking Change: MapStruct 1.5.0.Final may require @Mapper annotations");
        System.out.println("                 to have explicit componentModel parameter");
        System.out.println("Old Pattern: @Mapper");
        System.out.println("New Pattern: @Mapper(componentModel = \"default\")");
        System.out.println("Transformation: Add componentModel parameter to parameterless @Mapper annotations");
        System.out.println("Applicability: Any project using org.mapstruct.Mapper annotation");
    }
}