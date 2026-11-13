package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <project-root-directory>");
            System.exit(1);
        }
        
        String projectRoot = args[0];
        System.out.println("Analyzing project at: " + projectRoot);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        
        try {
            // Just analyze the application module where mappers are
            launcher.addInputResource(projectRoot + "/application/src/main/java");
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Find all types with @Mapper annotation
            List<CtType<?>> mapperTypes = new ArrayList<>();
            for (CtType<?> type : model.getElements(new TypeFilter<>(CtType.class))) {
                for (CtAnnotation<?> annotation : type.getAnnotations()) {
                    if (annotation.getAnnotationType() != null && 
                        annotation.getAnnotationType().getQualifiedName() != null &&
                        annotation.getAnnotationType().getQualifiedName().contains("Mapper")) {
                        mapperTypes.add(type);
                        break;
                    }
                }
            }
            
            if (!mapperTypes.isEmpty()) {
                System.out.println("\nFound " + mapperTypes.size() + " MapStruct mapper(s):");
                for (CtType<?> type : mapperTypes) {
                    System.out.println("  - " + type.getQualifiedName());
                }
                
                System.out.println("\n⚠️  IMPORTANT: MapStruct version compatibility check");
                System.out.println("==============================================");
                System.out.println("The project uses MapStruct annotations.");
                System.out.println("If you encounter 'Couldn't retrieve @Mapper annotation' errors,");
                System.out.println("it's likely due to version mismatch between:");
                System.out.println("  - org.mapstruct:mapstruct (runtime)");
                System.out.println("  - org.mapstruct:mapstruct-processor (annotation processor)");
                System.out.println("\n💡 RECOMMENDED FIX:");
                System.out.println("Ensure both dependencies use the SAME version (e.g., 1.5.0.Final):");
                System.out.println("\n<dependency>");
                System.out.println("    <groupId>org.mapstruct</groupId>");
                System.out.println("    <artifactId>mapstruct</artifactId>");
                System.out.println("    <version>1.5.0.Final</version>");
                System.out.println("</dependency>");
                System.out.println("<dependency>");
                System.out.println("    <groupId>org.mapstruct</groupId>");
                System.out.println("    <artifactId>mapstruct-processor</artifactId>");
                System.out.println("    <version>1.5.0.Final</version>");
                System.out.println("    <scope>provided</scope>");
                System.out.println("</dependency>");
                System.out.println("\nThis transformation detects MapStruct usage and alerts about");
                System.out.println("potential version compatibility issues.");
            } else {
                System.out.println("\nNo MapStruct mappers found in the project.");
            }
            
        } catch (Exception e) {
            System.err.println("Error analyzing project: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}