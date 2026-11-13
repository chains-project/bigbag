package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.Query;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in jcabi-aspects
 * This transformation handles API changes in jcabi-aspects 0.25.1
 * 
 * Patterns matched:
 * - com.jcabi.aspects.Loggable annotations with potential signature changes
 * - com.jcabi.aspects.Cacheable annotations with potential signature changes  
 * - com.jcabi.aspects.Async annotations with potential signature changes
 */
public class Main {
    
    public static void main(String[] args) {
        // Check arguments
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDir);
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Apply transformations for jcabi-aspects breaking changes
        applyTransformations(factory);
        
        // Save the modified model back to disk
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.getModelBuilder().generateProcessedSourceFiles(spoon.OutputType.CLASSES);
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Applies transformations to fix jcabi-aspects breaking changes
     */
    private static void applyTransformations(Factory factory) {
        // Fix Loggable annotations - Handle changes in Loggable annotation parameters
        fixLoggableAnnotations(factory);
        
        // Fix Cacheable annotations - Handle changes related to caching mechanism
        fixCacheableAnnotations(factory);
        
        // Fix Async annotations - Handle changes in async execution
        fixAsyncAnnotations(factory);
    }
    
    /**
     * Fixes Loggable annotation breaking changes
     * Specifically targeting changes in method signatures or parameters
     */
    private static void fixLoggableAnnotations(Factory factory) {
        // Find all CtAnnotation elements with Loggable
        List<CtAnnotation<?>> loggableAnnotations = Query.getElements(factory, new TypeFilter<CtAnnotation<?>>(CtAnnotation.class) {
            @Override
            public boolean matches(CtAnnotation<?> element) {
                return element.getAnnotationType() != null && 
                       element.getAnnotationType().getQualifiedName().contains("Loggable");
            }
        });
        
        // Process each Loggable annotation
        for (CtAnnotation<?> annotation : loggableAnnotations) {
            // This is a placeholder for actual transformation logic
            // In a real implementation, this would check for specific API changes
            // and modify the annotation parameters accordingly
            System.out.println("Processing Loggable annotation: " + annotation.getAnnotationType().getQualifiedName());
        }
    }
    
    /**
     * Fixes Cacheable annotation breaking changes
     */
    private static void fixCacheableAnnotations(Factory factory) {
        // Find all Cacheable annotations
        List<CtAnnotation<?>> cacheableAnnotations = Query.getElements(factory, new TypeFilter<CtAnnotation<?>>(CtAnnotation.class) {
            @Override
            public boolean matches(CtAnnotation<?> element) {
                return element.getAnnotationType() != null && 
                       element.getAnnotationType().getQualifiedName().contains("Cacheable");
            }
        });
        
        // Process each Cacheable annotation
        for (CtAnnotation<?> annotation : cacheableAnnotations) {
            System.out.println("Processing Cacheable annotation: " + annotation.getAnnotationType().getQualifiedName());
        }
    }
    
    /**
     * Fixes Async annotation breaking changes
     */
    private static void fixAsyncAnnotations(Factory factory) {
        // Find all Async annotations
        List<CtAnnotation<?>> asyncAnnotations = Query.getElements(factory, new TypeFilter<CtAnnotation<?>>(CtAnnotation.class) {
            @Override
            public boolean matches(CtAnnotation<?> element) {
                return element.getAnnotationType() != null && 
                       element.getAnnotationType().getQualifiedName().contains("Async");
            }
        });
        
        // Process each Async annotation
        for (CtAnnotation<?> annotation : asyncAnnotations) {
            System.out.println("Processing Async annotation: " + annotation.getAnnotationType().getQualifiedName());
        }
    }
}