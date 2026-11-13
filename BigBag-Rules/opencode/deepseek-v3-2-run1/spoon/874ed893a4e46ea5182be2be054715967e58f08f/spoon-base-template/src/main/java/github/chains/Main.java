package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("This transformation fixes the Jackson 2.14+ breaking change where JsonSerializeAs was removed.");
            System.err.println("It removes all imports and usages of com.fasterxml.jackson.annotation.JsonSerializeAs");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: Remove JsonSerializeAs (removed in Jackson 2.14+)");
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Track changes
        int importRemovals = 0;
        int annotationRemovals = 0;
        
        // Remove imports of JsonSerializeAs
        List<CtImport> importsToRemove = new ArrayList<>();
        for (CtImport ctImport : model.getElements(new TypeFilter<>(CtImport.class))) {
            String importStr = ctImport.toString();
            // Match exact import or wildcard import
            if (importStr.contains("com.fasterxml.jackson.annotation.JsonSerializeAs") ||
                (importStr.contains("com.fasterxml.jackson.annotation.*") && 
                 model.getElements(new TypeFilter<CtAnnotation>(CtAnnotation.class) {
                    @Override
                    public boolean matches(CtAnnotation element) {
                        return element.getAnnotationType().toString().contains("JsonSerializeAs");
                    }
                 }).size() > 0)) {
                importsToRemove.add(ctImport);
            }
        }
        
        for (CtImport ctImport : importsToRemove) {
            ctImport.delete();
            importRemovals++;
        }
        
        // Remove @JsonSerializeAs annotations  
        List<CtAnnotation<?>> annotationsToRemove = new ArrayList<>();
        for (CtAnnotation<?> annotation : model.getElements(new TypeFilter<>(CtAnnotation.class))) {
            CtTypeReference<?> annotationType = annotation.getAnnotationType();
            if (annotationType != null) {
                String typeName = annotationType.getQualifiedName();
                if (typeName != null && typeName.equals("com.fasterxml.jackson.annotation.JsonSerializeAs")) {
                    annotationsToRemove.add(annotation);
                }
            }
        }
        
        for (CtAnnotation<?> annotation : annotationsToRemove) {
            annotation.delete();
            annotationRemovals++;
        }
        
        // Apply transformations
        if (importRemovals > 0 || annotationRemovals > 0) {
            System.out.println("Applied transformations:");
            System.out.println("  - Removed " + importRemovals + " imports of JsonSerializeAs");
            System.out.println("  - Removed " + annotationRemovals + " @JsonSerializeAs annotations");
            
            // Write transformed code back
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation complete. Code written back to: " + sourceDir);
            System.out.println("Note: You may also need to update your pom.xml to use compatible Jackson versions:");
            System.out.println("  - jackson-databind: [2.14.0-rc1,)");
            System.out.println("  - jackson-annotations: [2.14.0-rc1,)");
            System.out.println("  - jackson-core: [2.14.0-rc1,)");
        } else {
            System.out.println("No JsonSerializeAs imports or annotations found. No transformations applied.");
            System.out.println("Note: If you're still experiencing issues, check your pom.xml for incompatible Jackson versions.");
        }
    }
}