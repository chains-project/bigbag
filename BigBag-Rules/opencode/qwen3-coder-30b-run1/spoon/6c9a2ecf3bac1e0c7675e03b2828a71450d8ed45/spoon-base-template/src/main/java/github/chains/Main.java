package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.factory.Factory;

import java.util.Collection;

public class Main {
    public static void main(String[] args) {
        // Check if source directory is provided
        if (args.length == 0) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setNoClasspath(true);
        
        // Add source directory
        launcher.addInputResource(sourceDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Get all Java files
        Collection<CtType<?>> types = model.getAllTypes();
        
        // Process each type to identify MapStruct issues
        int mapperCount = 0;
        for (CtType<?> type : types) {
            // Check if the class has @Mapper annotation
            if (hasMapperAnnotation(type)) {
                mapperCount++;
                System.out.println("Found @Mapper annotation in: " + type.getQualifiedName());
            }
        }
        
        System.out.println("Found " + mapperCount + " @Mapper annotations in " + sourceDirectory);
        System.out.println("This transformation identifies MapStruct @Mapper annotations for manual fixing.");
    }
    
    private static boolean hasMapperAnnotation(CtType<?> type) {
        // Check if the type has @Mapper annotation
        return type.getAnnotations().stream()
                .anyMatch(ann -> ann.getAnnotationType().getSimpleName().equals("Mapper"));
    }
}