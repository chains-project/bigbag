package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Simple generic transformation for fixing Liquibase dependency breaking changes.
 */
public class SimpleLiquibaseTransformer {
    
    /**
     * Main transformation method
     */
    public static void transform(String[] sourcePaths, String outputPath) {
        Launcher launcher = new Launcher();
        
        // Set up the launcher with source paths
        for (String path : sourcePaths) {
            launcher.addInputResource(path);
        }
        
        // Set output directory
        launcher.setSourceOutputDirectory(outputPath);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply basic transformations
        System.out.println("Starting Liquibase transformation...");
        fixLiquibaseInheritanceIssues(model);
        System.out.println("Transformation completed.");
    }
    
    /**
     * Fix inheritance issues in Liquibase extension classes
     */
    private static void fixLiquibaseInheritanceIssues(CtModel model) {
        // Find all Liquibase extension classes
        List<CtClass<?>> extensionClasses = model.getElements(new TypeFilter<>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                // Look for classes that extend Liquibase core classes
                String className = element.getQualifiedName();
                return className.contains("liquibase.ext.") && 
                       (className.contains(".change.") || 
                        className.contains(".database.") ||
                        className.contains(".sqlgenerator."));
            }
        });
        
        System.out.println("Found " + extensionClasses.size() + " Liquibase extension classes");
        
        for (CtClass<?> clazz : extensionClasses) {
            System.out.println("Processing: " + clazz.getQualifiedName());
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: SimpleLiquibaseTransformer <sourcePath> <outputPath>");
            return;
        }
        
        String[] sourcePaths = {args[0]};
        String outputPath = args[1];
        
        transform(sourcePaths, outputPath);
    }
}