package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {
    // Mapping from old class to new class for imports
    private static final Map<String, String> IMPORT_MAPPING = new HashMap<>();
    // Mapping from "oldClass.method" to new class
    private static final Map<String, String> METHOD_MAPPING = new HashMap<>();
    
    private static void loadMappings(String mappingFile) {
        // Default mappings for hamcrest 2.2
        IMPORT_MAPPING.put("org.hamcrest.Matchers", "org.hamcrest.CoreMatchers");
        
        // Try to load from file if exists
        if (mappingFile != null) {
            try (BufferedReader br = new BufferedReader(new FileReader(mappingFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        
                        if (key.contains(".")) {
                            // Check if it's class.method format
                            int lastDot = key.lastIndexOf('.');
                            String beforeLastDot = key.substring(0, lastDot);
                            String afterLastDot = key.substring(lastDot + 1);
                            
                            // If the part after last dot looks like a method name (starts with lowercase)
                            if (afterLastDot.length() > 0 && Character.isLowerCase(afterLastDot.charAt(0))) {
                                // Method mapping: oldClass.method=newClass
                                METHOD_MAPPING.put(key, value);
                            } else {
                                // Class mapping: oldClass=newClass
                                IMPORT_MAPPING.put(key, value);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not load mapping file: " + e.getMessage());
            }
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory> [mapping-file]");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String mappingFile = args.length > 1 ? args[1] : null;
        
        System.out.println("Applying transformation to: " + sourceDir);
        if (mappingFile != null) {
            System.out.println("Using mapping file: " + mappingFile);
        }
        
        loadMappings(mappingFile);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Get factory
        final spoon.reflect.factory.Factory factory = launcher.getFactory();
        
        // Create scanner to transform the code
        CtScanner scanner = new CtScanner() {
            @Override
            public void visitCtImport(CtImport ctImport) {
                super.visitCtImport(ctImport);
                
                // Check if this is an import that needs transformation
                if (ctImport.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                    String oldName = typeRef.getQualifiedName();
                    
                    if (IMPORT_MAPPING.containsKey(oldName)) {
                        String newName = IMPORT_MAPPING.get(oldName);
                        CtTypeReference<?> newTypeRef = factory.Type().createReference(newName);
                        ctImport.setReference(newTypeRef);
                        System.out.println("Transformed import: " + oldName + " -> " + newName);
                    }
                }
            }
            
            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                super.visitCtInvocation(invocation);
                
                // Check if this is a static method call to a class that needs transformation
                if (invocation.getTarget() instanceof CtTypeAccess) {
                    CtTypeAccess<?> typeAccess = (CtTypeAccess<?>) invocation.getTarget();
                    CtTypeReference<?> typeRef = typeAccess.getAccessedType();
                    
                    if (typeRef != null) {
                        String typeName = typeRef.getQualifiedName();
                        String methodName = invocation.getExecutable().getSimpleName();
                        String fullMethodKey = typeName + "." + methodName;
                        
                        // Check if this specific method needs to be moved to a different class
                        if (METHOD_MAPPING.containsKey(fullMethodKey)) {
                            String newClassName = METHOD_MAPPING.get(fullMethodKey);
                            CtTypeReference<?> newTypeRef = factory.Type().createReference(newClassName);
                            typeAccess.setType(newTypeRef);
                            System.out.println("Transformed method " + fullMethodKey + " to class " + newClassName);
                        }
                        // Check if the class itself needs transformation (default mapping)
                        else if (IMPORT_MAPPING.containsKey(typeName)) {
                            String newClassName = IMPORT_MAPPING.get(typeName);
                            CtTypeReference<?> newTypeRef = factory.Type().createReference(newClassName);
                            typeAccess.setType(newTypeRef);
                            System.out.println("Transformed class: " + typeName + " -> " + newClassName + " for method " + methodName);
                        }
                    }
                }
            }
        };
        
        // Apply transformation
        model.getRootPackage().accept(scanner);
        
        // Output the transformed code
        launcher.setSourceOutputDirectory("/tmp/transformed-generic");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to /tmp/transformed-generic");
        System.out.println("\nSummary:");
        System.out.println("- Import mappings applied: " + IMPORT_MAPPING.size());
        System.out.println("- Method mappings applied: " + METHOD_MAPPING.size());
        System.out.println("\nTo use this transformation for other projects:");
        System.out.println("1. Create a mapping file with lines like:");
        System.out.println("   old.package.OldClass=new.package.NewClass");
        System.out.println("   old.package.OldClass.methodName=new.package.NewClass");
        System.out.println("2. Run: java github.chains.Main <source-dir> <mapping-file>");
    }
}