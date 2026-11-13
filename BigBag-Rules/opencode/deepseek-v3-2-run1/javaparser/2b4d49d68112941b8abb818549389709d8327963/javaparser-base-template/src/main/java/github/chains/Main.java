package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    
    // Map of old package imports to new package imports
    private static final Map<String, String> PACKAGE_MAPPINGS = createPackageMappings();
    
    // Map of old method names to new method names
    private static final Map<String, String> METHOD_MAPPINGS = createMethodMappings();
    
    // Map of old fully qualified types to new fully qualified types
    private static final Map<String, String> TYPE_MAPPINGS = createTypeMappings();
    
    private static Map<String, String> createPackageMappings() {
        Map<String, String> map = new HashMap<>();
        map.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        map.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        map.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        map.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        // com.hazelcast.core.MemberAttributeEvent removed in new API
        map.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        map.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        // EntryListener and EntryEvent remain in com.hazelcast.core
        // LifecycleEvent and LifecycleListener remain in com.hazelcast.core
        return map;
    }
    
    private static Map<String, String> createMethodMappings() {
        Map<String, String> map = new HashMap<>();
        map.put("getStringAttribute", "getAttribute");
        return map;
    }
    
    private static Map<String, String> createTypeMappings() {
        Map<String, String> map = new HashMap<>();
        map.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        map.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        map.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        map.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        // com.hazelcast.core.MemberAttributeEvent removed in new API
        map.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        map.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        // EntryListener and EntryEvent remain in com.hazelcast.core
        // com.hazelcast.config.MaxSizeConfig removed in new API
        map.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        return map;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(File directory) throws FileNotFoundException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory.getPath());
        }
        
        List<File> javaFiles = findJavaFiles(directory);
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        JavaParser parser = new JavaParser();
        
        for (File javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile.getPath());
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse: " + javaFile)
                );
                
                boolean modified = applyTransformations(cu);
                
                if (modified) {
                    // Write back the modified file
                    cu.getStorage().ifPresentOrElse(
                        storage -> {
                            try {
                                storage.save();
                                System.out.println("  -> Updated: " + javaFile.getPath());
                            } catch (Exception e) {
                                System.err.println("  -> Failed to save: " + e.getMessage());
                            }
                        },
                        () -> System.err.println("  -> No storage available for: " + javaFile.getPath())
                    );
                } else {
                    System.out.println("  -> No changes needed");
                }
                
            } catch (Exception e) {
                System.err.println("  -> Error processing: " + e.getMessage());
            }
        }
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(directory, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
    
    private static boolean applyTransformations(CompilationUnit cu) {
        boolean[] modified = new boolean[]{false};
        
        // Visitor for updating imports
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ImportDeclaration importDecl, Void arg) {
                String importName = importDecl.getNameAsString();
                
                // Check if this import needs to be updated
                if (PACKAGE_MAPPINGS.containsKey(importName)) {
                    String newImport = PACKAGE_MAPPINGS.get(importName);
                    
                    if (newImport == null) {
                        // Import removed in new API - delete this import
                        modified[0] = true;
                        return null; // Remove the import
                    } else if (!importName.equals(newImport)) {
                        // Update to new import
                        modified[0] = true;
                        return new ImportDeclaration(newImport, importDecl.isStatic(), importDecl.isAsterisk());
                    }
                }
                
                // Check for com.hazelcast.monitor.* imports
                if (importName.startsWith("com.hazelcast.monitor.")) {
                    // Update to com.hazelcast.map for LocalMapStats, otherwise internal.monitor
                    String newImport;
                    if (importName.equals("com.hazelcast.monitor.LocalMapStats")) {
                        newImport = "com.hazelcast.map.LocalMapStats";
                    } else {
                        newImport = importName.replace("com.hazelcast.monitor.", "com.hazelcast.internal.monitor.");
                    }
                    modified[0] = true;
                    return new ImportDeclaration(newImport, importDecl.isStatic(), importDecl.isAsterisk());
                }
                
                // Handle MemberAttributeEvent - remove import
                if (importName.equals("com.hazelcast.core.MemberAttributeEvent")) {
                    modified[0] = true;
                    return null; // Remove the import
                }
                
                return importDecl;
            }
        }, null);
        
        // Visitor for updating method calls
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(MethodCallExpr methodCall, Void arg) {
                String methodName = methodCall.getNameAsString();
                
                // Check if this method needs to be renamed
                if (METHOD_MAPPINGS.containsKey(methodName)) {
                    String newMethodName = METHOD_MAPPINGS.get(methodName);
                    if (!methodName.equals(newMethodName)) {
                        methodCall.setName(newMethodName);
                        modified[0] = true;
                    }
                }
                
                return methodCall;
            }
        }, null);
        
        // Visitor for updating type references in code
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(NameExpr nameExpr, Void arg) {
                // This is a simplified approach - in a real implementation,
                // we would need to resolve the type and check if it matches
                // the old fully qualified names
                return nameExpr;
            }
        }, null);
        
        return modified[0];
    }
}