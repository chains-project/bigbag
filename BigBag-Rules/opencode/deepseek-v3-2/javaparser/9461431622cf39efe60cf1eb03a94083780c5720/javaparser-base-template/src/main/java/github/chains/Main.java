package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing breaking API changes in snmp4j-agent 3.6.5
 * 
 * Breaking Change Pattern:
 * - Old API: Methods returning collections with raw or non-wildcard ManagedObject types
 * - New API: Methods returning collections with ManagedObject<?> wildcard type
 * - Example: getRegistry() returns SortedMap<MOScope, ManagedObject<?>> instead of SortedMap<MOScope, ManagedObject>
 * 
 * Transformation Rules:
 * 1. SortedMap<MOScope, ManagedObject> -> SortedMap<MOScope, ManagedObject<?>>
 * 2. Map<MOScope, ManagedObject> -> Map<MOScope, ManagedObject<?>>
 * 3. Collection<ManagedObject> -> Collection<ManagedObject<?>>
 * 4. List<ManagedObject> -> List<ManagedObject<?>>
 * 5. Set<ManagedObject> -> Set<ManagedObject<?>>
 * 6. Iterator<ManagedObject> -> Iterator<ManagedObject<?>>
 * 7. etc.
 * 
 * Note: 'implements ManagedObject' requires different handling (should be ManagedObject<SubRequest<?>>)
 * but that's a separate transformation.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Transforms raw ManagedObject types in generic collections to ManagedObject<?>");
            System.err.println("Fixes breaking API changes in snmp4j-agent 3.6.5");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir.toAbsolutePath());
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(() -> 
                    new RuntimeException("Failed to parse " + javaFile));
                
                final boolean[] modified = {false};
                
                // Look for generic types containing ManagedObject without type arguments
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(ClassOrInterfaceType type, Void arg) {
                        // Check if this is a generic type (like Map, List, Collection, etc.)
                        NodeList<Type> typeArguments = type.getTypeArguments().orElse(null);
                        if (typeArguments != null && !typeArguments.isEmpty()) {
                            // Check each type argument for raw ManagedObject
                            for (int i = 0; i < typeArguments.size(); i++) {
                                Type typeArg = typeArguments.get(i);
                                if (typeArg instanceof ClassOrInterfaceType) {
                                    ClassOrInterfaceType classTypeArg = (ClassOrInterfaceType) typeArg;
                                    if (classTypeArg.getNameAsString().equals("ManagedObject")) {
                                        if (!classTypeArg.getTypeArguments().isPresent()) {
                                            // Change ManagedObject to ManagedObject<?>
                                            ClassOrInterfaceType newTypeArg = new ClassOrInterfaceType();
                                            newTypeArg.setName("ManagedObject");
                                            newTypeArg.setTypeArguments(new WildcardType());
                                            typeArguments.set(i, newTypeArg);
                                            modified[0] = true;
                                            System.out.println("  Fixed: " + type.getNameAsString() + 
                                                " with raw ManagedObject in " + javaFile.getFileName());
                                        }
                                    }
                                }
                            }
                        }
                        
                        return super.visit(type, arg);
                    }
                }, null);
                
                if (modified[0]) {
                    filesModified++;
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("Modified: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Modified " + filesModified + " files");
        System.out.println("\nSummary:");
        System.out.println("This transformation fixes the breaking API change in snmp4j-agent 3.6.5");
        System.out.println("where methods now return collections with ManagedObject<?> instead of raw ManagedObject.");
        System.out.println("The transformation is generic and can be applied to any project affected by this change.");
    }
}