package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for Hazelcast 4.0.5 breaking API changes.
 * This rule handles package relocations from com.hazelcast.core to com.hazelcast.cluster
 * and other package structure changes.
 */
public class Main {
    
    // Mapping of old package patterns to new package patterns
    private static final List<PackageMapping> PACKAGE_MAPPINGS = List.of(
        new PackageMapping("com.hazelcast.core.Member", "com.hazelcast.cluster.Member"),
        new PackageMapping("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster"),
        new PackageMapping("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent"),
        new PackageMapping("com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MemberAttributeEvent"),
        new PackageMapping("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener"),
        new PackageMapping("com.hazelcast.core.IMap", "com.hazelcast.map.IMap"),
        new PackageMapping("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent"),
        new PackageMapping("com.hazelcast.map.listener.MapEvent", "com.hazelcast.map.MapEvent"),
        new PackageMapping("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats"),
        new PackageMapping("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig")
    );

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser-base-template.jar <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        return Files.walk(startDir)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }

    private static boolean transformFile(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            JavaParser parser = new JavaParser(new ParserConfiguration());
            var parseResult = parser.parse(content);
            
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                System.err.println("Failed to parse file: " + javaFile);
                return false;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            boolean modified = false;
            
            // Transform imports
            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                
                // Special case: MemberAttributeEvent was completely removed
                if (importName.equals("com.hazelcast.cluster.MemberAttributeEvent")) {
                    importsToRemove.add(importDecl);
                    modified = true;
                    System.out.println("Removed import: " + importName + " (class was removed in Hazelcast 4.0.5)");
                    continue;
                }
                
                for (PackageMapping mapping : PACKAGE_MAPPINGS) {
                    if (mapping.matches(importName)) {
                        String newImport = mapping.transform(importName);
                        importDecl.setName(new Name(newImport));
                        modified = true;
                        System.out.println("Transformed import: " + importName + " -> " + newImport);
                        break;
                    }
                }
            }
            
            // Remove imports after iteration
            for (ImportDeclaration importDecl : importsToRemove) {
                importDecl.remove();
            }
            
            // Transform fully qualified type references in code
            cu.walk(ClassOrInterfaceType.class, node -> {
                String typeName = node.getNameAsString();
                for (PackageMapping mapping : PACKAGE_MAPPINGS) {
                    if (mapping.matches(typeName)) {
                        String newType = mapping.transform(typeName);
                        node.setName(newType);
                        System.out.println("Transformed type: " + typeName + " -> " + newType);
                        break;
                    }
                }
            });
            
            // Remove memberAttributeChanged method (was removed from MembershipListener interface)
            boolean[] methodRemoved = {false};
            cu.walk(MethodDeclaration.class, method -> {
                if ("memberAttributeChanged".equals(method.getNameAsString())) {
                    Node parent = method.getParentNode().orElse(null);
                    if (parent != null) {
                        method.remove();
                        methodRemoved[0] = true;
                        System.out.println("Removed method: memberAttributeChanged (method was removed from MembershipListener interface)");
                    }
                }
            });
            
            if (methodRemoved[0]) {
                modified = true;
            }
            
            if (modified) {
                String transformedContent = cu.toString();
                Files.writeString(javaFile, transformedContent);
                System.out.println("Updated file: " + javaFile);
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Represents a package mapping from old to new package structure
     */
    static class PackageMapping {
        private final String oldPattern;
        private final String newPattern;
        
        PackageMapping(String oldPattern, String newPattern) {
            this.oldPattern = oldPattern;
            this.newPattern = newPattern;
        }
        
        boolean matches(String importName) {
            // Exact match for fully qualified class names
            if (importName.equals(oldPattern)) {
                return true;
            }
            
            // Package prefix match (e.g., com.hazelcast.core.*)
            if (oldPattern.endsWith(".*")) {
                String packagePrefix = oldPattern.substring(0, oldPattern.length() - 2);
                return importName.startsWith(packagePrefix);
            }
            
            // Simple class name match (when imported with wildcard)
            if (importName.contains(".")) {
                String simpleName = importName.substring(importName.lastIndexOf('.') + 1);
                return simpleName.equals(oldPattern);
            }
            
            return false;
        }
        
        String transform(String importName) {
            // Exact replacement for fully qualified names
            if (importName.equals(oldPattern)) {
                return newPattern;
            }
            
            // Package prefix replacement
            if (oldPattern.endsWith(".*")) {
                String oldPackage = oldPattern.substring(0, oldPattern.length() - 2);
                String newPackage = newPattern.endsWith(".*") ? 
                    newPattern.substring(0, newPattern.length() - 2) : newPattern;
                
                if (importName.startsWith(oldPackage)) {
                    String remaining = importName.substring(oldPackage.length());
                    return newPackage + remaining;
                }
            }
            
            // Simple class name replacement
            if (importName.contains(".")) {
                String packagePart = importName.substring(0, importName.lastIndexOf('.'));
                String simpleName = importName.substring(importName.lastIndexOf('.') + 1);
                
                if (simpleName.equals(oldPattern)) {
                    return packagePart + "." + newPattern;
                }
            }
            
            return importName;
        }
    }
}