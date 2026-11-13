package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.SpoonClassNotFoundException;

import java.io.File;
import java.util.*;

/**
 * Generic Spoon transformation for Jakarta EE migration (Dropwizard 4.0.0+).
 * 
 * This transformation handles the breaking change from javax.* to jakarta.* packages
 * that occurred in Dropwizard 4.0.0 when migrating from JAX-RS 2.x to Jakarta RS 3.x.
 * 
 * The transformation is generic and reusable for any Java project affected by
 * the Jakarta EE 9+ migration. It transforms:
 * - javax.ws.rs.* → jakarta.ws.rs.*
 * - javax.servlet.* → jakarta.servlet.*  
 * - javax.annotation.* → jakarta.annotation.*
 * - javax.validation.* → jakarta.validation.*
 * - javax.xml.bind.* → jakarta.xml.bind.*
 * 
 * Usage: java -jar transformation.jar <sourceDir> <outputDir>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar transformation.jar <sourceDir> <outputDir>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src /path/to/transformed/src");
            System.err.println("\nThis transformation fixes Dropwizard 4.0.0+ compilation errors");
            System.err.println("by migrating from javax.* to jakarta.* packages.");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];

        System.out.println("Applying generic Jakarta EE migration transformation");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        System.out.println("\nTransforming packages:");
        System.out.println("  javax.ws.rs.*      → jakarta.ws.rs.*");
        System.out.println("  javax.servlet.*    → jakarta.servlet.*");
        System.out.println("  javax.annotation.* → jakarta.annotation.*");
        System.out.println("  javax.validation.* → jakarta.validation.*");
        System.out.println("  javax.xml.bind.*   → jakarta.xml.bind.*");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addInputResource(sourceDir);
        CtModel model = launcher.buildModel();
        
        applyJakartaTransformations(model);
        
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed successfully!");
        System.out.println("Transformed code written to: " + outputDir);
    }
    
    private static void applyJakartaTransformations(CtModel model) {
        Map<String, String> packageMappings = createPackageMappings();
        
        model.getRootPackage().accept(new CtScanner() {
            @Override
            public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
                super.visitCtTypeReference(reference);
                transformTypeReference(reference, packageMappings);
            }
        });
    }
    
    private static Map<String, String> createPackageMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("javax.ws.rs", "jakarta.ws.rs");
        mappings.put("javax.servlet", "jakarta.servlet");
        mappings.put("javax.annotation", "jakarta.annotation");
        mappings.put("javax.validation", "jakarta.validation");
        mappings.put("javax.xml.bind", "jakarta.xml.bind");
        return mappings;
    }
    
    private static <T> void transformTypeReference(CtTypeReference<T> reference, Map<String, String> packageMappings) {
        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName != null) {
            for (Map.Entry<String, String> mapping : packageMappings.entrySet()) {
                if (qualifiedName.startsWith(mapping.getKey() + ".")) {
                    String newQualifiedName = mapping.getValue() + qualifiedName.substring(mapping.getKey().length());
                    try {
                        CtTypeReference<T> newRef = reference.getFactory().Type().createReference(newQualifiedName);
                        reference.replace(newRef);
                    } catch (SpoonClassNotFoundException e) {
                        System.err.println("Warning: Type " + newQualifiedName + " not found in classpath");
                    }
                    break;
                }
            }
        }
    }
}