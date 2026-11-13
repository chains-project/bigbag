package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <sourceDirectory> <outputDirectory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String outputDirectory = args[1];
        
        // Configuration: Map of removed class fields to their replacement values
        Map<String, Map<String, Object>> removedClassMappings = new HashMap<>();
        
        // For jcabi-aspects.Tv class removal
        Map<String, Object> tvMappings = new HashMap<>();
        tvMappings.put("FIVE", 5);
        tvMappings.put("HUNDRED", 100);
        tvMappings.put("THOUSAND", 1000);
        // Add more mappings as needed for other constants
        tvMappings.put("TEN", 10);
        tvMappings.put("TWENTY", 20);
        tvMappings.put("FIFTY", 50);
        tvMappings.put("MILLION", 1000000);
        removedClassMappings.put("com.jcabi.aspects.Tv", tvMappings);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);
        
        // Configure launcher
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        int replacements = 0;
        
        // Process each removed class mapping
        for (Map.Entry<String, Map<String, Object>> entry : removedClassMappings.entrySet()) {
            String removedClassName = entry.getKey();
            Map<String, Object> fieldMappings = entry.getValue();
            
            // Find all field accesses to the removed class
            List<CtFieldAccess<?>> fieldAccesses = model.getElements(new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class) {
                @Override
                public boolean matches(CtFieldAccess<?> fieldAccess) {
                    CtFieldReference<?> fieldRef = fieldAccess.getVariable();
                    if (fieldRef != null) {
                        CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
                        if (declaringType != null) {
                            return declaringType.getQualifiedName().equals(removedClassName);
                        }
                    }
                    return false;
                }
            });
            
            // Replace field accesses with literal values
            for (CtFieldAccess<?> fieldAccess : fieldAccesses) {
                String fieldName = fieldAccess.getVariable().getSimpleName();
                if (fieldMappings.containsKey(fieldName)) {
                    Object replacementValue = fieldMappings.get(fieldName);
                    CtLiteral<?> literal = factory.createLiteral(replacementValue);
                    fieldAccess.replace(literal);
                    replacements++;
                    System.out.println("Replaced " + removedClassName + "." + fieldName + " with " + replacementValue);
                } else {
                    System.err.println("WARNING: No mapping found for " + removedClassName + "." + fieldName);
                }
            }
        }
        
        // Write transformed code
        launcher.prettyprint();
        
        // Post-process: remove imports of removed classes
        removeImportsOfRemovedClasses(outputDirectory, removedClassMappings.keySet());
        
        System.out.println("\nTransformation complete. Made " + replacements + " replacements.");
        System.out.println("Output written to: " + outputDirectory);
    }
    
    private static void removeImportsOfRemovedClasses(String outputDirectory, java.util.Set<String> removedClasses) {
        try {
            Files.walk(Paths.get(outputDirectory))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        boolean modified = false;
                        
                        for (String removedClass : removedClasses) {
                            // Remove import statements for the removed class
                            String importPattern = "import " + removedClass + ";";
                            String importPatternWildcard = "import " + removedClass + ".*;";
                            
                            if (content.contains(importPattern)) {
                                content = content.replace(importPattern, "");
                                modified = true;
                                System.out.println("Removed import: " + importPattern + " from " + file);
                            }
                            if (content.contains(importPatternWildcard)) {
                                content = content.replace(importPatternWildcard, "");
                                modified = true;
                                System.out.println("Removed import: " + importPatternWildcard + " from " + file);
                            }
                        }
                        
                        if (modified) {
                            Files.writeString(file, content);
                        }
                    } catch (IOException e) {
                        System.err.println("Error processing file " + file + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
    }
}