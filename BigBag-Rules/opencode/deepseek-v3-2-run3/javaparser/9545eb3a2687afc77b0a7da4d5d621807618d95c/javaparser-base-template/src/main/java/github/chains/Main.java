package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation for handling breaking API changes.
 * 
 * This tool can be configured to handle:
 * 1. Package migrations (old.package.Class -> new.package.Class)
 * 2. Method renames (oldMethod -> newMethod)
 * 3. Removal of classes with warnings
 * 
 * For complex API changes that require code restructuring (e.g., 
 * MaxSizeConfig -> EvictionConfig), the tool flags them for manual intervention.
 */
public class Main {
    
    // Configuration structures - in a real tool, these would be loaded from config files
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    private static final Map<String, String> METHOD_MAPPINGS = new HashMap<>();
    private static final Set<String> REMOVED_CLASSES = new HashSet<>();
    
    static {
        // Example configuration for Hazelcast 3.x/4.x to 5.1 migration
        // Package migrations from old to new
        PACKAGE_MAPPINGS.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        PACKAGE_MAPPINGS.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        PACKAGE_MAPPINGS.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        PACKAGE_MAPPINGS.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        PACKAGE_MAPPINGS.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        PACKAGE_MAPPINGS.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        PACKAGE_MAPPINGS.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        
        // Classes that were removed (imports will be removed, usage will be flagged)
        REMOVED_CLASSES.add("com.hazelcast.core.MemberAttributeEvent");
        REMOVED_CLASSES.add("com.hazelcast.config.MaxSizeConfig");
        
        // Method name changes (simple renames)
        METHOD_MAPPINGS.put("getStringAttribute", "getAttribute");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.err.println("\nThis tool performs automatic refactoring for breaking API changes.");
            System.err.println("\nSupported transformations:");
            System.err.println("  - Updates import statements for moved classes");
            System.err.println("  - Renames simple method calls");
            System.err.println("  - Flags usage of removed classes for manual intervention");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== API Migration Transformation Tool ===");
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Mappings configured: " + PACKAGE_MAPPINGS.size() + " package mappings, " + 
                          METHOD_MAPPINGS.size() + " method mappings");
        
        try {
            Path sourcePath = Paths.get(sourceDir);
            if (!Files.exists(sourcePath)) {
                System.err.println("ERROR: Source directory does not exist: " + sourceDir);
                System.exit(1);
            }
            
            List<Path> javaFiles = Files.walk(sourcePath)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("\nFound " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            List<String> warnings = new ArrayList<>();
            
            for (Path filePath : javaFiles) {
                TransformationResult result = transformFile(filePath);
                if (result.modified) {
                    transformedFiles++;
                }
                warnings.addAll(result.warnings);
            }
            
            System.out.println("\n=== Transformation Complete ===");
            System.out.println("Transformed " + transformedFiles + " files");
            
            if (!warnings.isEmpty()) {
                System.out.println("\n=== Warnings (require manual intervention) ===");
                for (String warning : warnings) {
                    System.out.println(warning);
                }
                System.out.println("\nTotal warnings: " + warnings.size());
            }
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static class TransformationResult {
        boolean modified;
        List<String> warnings = new ArrayList<>();
    }
    
    private static TransformationResult transformFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        TransformationResult result = new TransformationResult();
        
        try (var in = Files.newInputStream(filePath)) {
            CompilationUnit cu = parser.parse(in).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            // Transform imports
            result.modified |= transformImports(cu, result.warnings);
            
            // Transform method calls
            result.modified |= transformMethodCalls(cu, result.warnings);
            
            // Check for usage of removed classes
            result.modified |= checkRemovedClasses(cu, filePath, result.warnings);
            
            if (result.modified) {
                // Write back the transformed file
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String transformedCode = printer.print(cu);
                
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(transformedCode);
                }
                
                System.out.println("  Transformed: " + filePath);
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error transforming " + filePath + ": " + e.getMessage());
            result.warnings.add("ERROR: Failed to transform " + filePath + ": " + e.getMessage());
            return result;
        }
    }
    
    private static boolean transformImports(CompilationUnit cu, List<String> warnings) {
        boolean modified = false;
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            if (PACKAGE_MAPPINGS.containsKey(importName)) {
                String newImport = PACKAGE_MAPPINGS.get(importName);
                importDecl.setName(newImport);
                modified = true;
            } else if (REMOVED_CLASSES.contains(importName)) {
                importsToRemove.add(importDecl);
                modified = true;
            }
        }
        
        // Remove imports marked for deletion
        for (ImportDeclaration importDecl : importsToRemove) {
            importDecl.remove();
        }
        
        return modified;
    }
    
    private static boolean transformMethodCalls(CompilationUnit cu, List<String> warnings) {
        class MethodCallTransformer extends ModifierVisitor<Void> {
            private boolean modified = false;
            
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                String methodName = n.getNameAsString();
                
                if (METHOD_MAPPINGS.containsKey(methodName)) {
                    String newMethodName = METHOD_MAPPINGS.get(methodName);
                    n.setName(newMethodName);
                    modified = true;
                }
                
                return super.visit(n, arg);
            }
        }
        
        MethodCallTransformer transformer = new MethodCallTransformer();
        transformer.visit(cu, null);
        return transformer.modified;
    }
    
    private static boolean checkRemovedClasses(CompilationUnit cu, Path filePath, List<String> warnings) {
        class RemovedClassChecker extends ModifierVisitor<Void> {
            @Override
            public Visitable visit(ObjectCreationExpr n, Void arg) {
                String typeName = n.getType().asString();
                
                // Check if this looks like a removed class (simple name match)
                // In a more sophisticated version, we'd check fully qualified names
                if (typeName.equals("MaxSizeConfig") || typeName.equals("MemberAttributeEvent")) {
                    int line = n.getRange().map(r -> r.begin.line).orElse(-1);
                    warnings.add("  " + filePath.getFileName() + ":" + line + 
                               " - Usage of removed class: " + typeName + 
                               " (needs manual conversion)");
                }
                return super.visit(n, arg);
            }
        }
        
        RemovedClassChecker checker = new RemovedClassChecker();
        checker.visit(cu, null);
        return false; // This check doesn't modify the AST, just collects warnings
    }
}