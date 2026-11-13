package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    
    // Generic mapping of old imports to new imports
    // This can be extended for any breaking dependency update
    private static final Map<String, String> IMPORT_MAPPINGS = new HashMap<>();
    
    // Mapping of old type names to new type names (for type references in code)
    private static final Map<String, String> TYPE_MAPPINGS = new HashMap<>();
    
    // Mapping of old method names to new method names
    private static final Map<String, String> METHOD_MAPPINGS = new HashMap<>();
    
    static {
        // Hazelcast 3.x/4.x to 5.1 migration patterns
        // Package relocation patterns
        IMPORT_MAPPINGS.put("com.hazelcast.core.Member", "com.hazelcast.cluster.Member");
        IMPORT_MAPPINGS.put("com.hazelcast.core.Cluster", "com.hazelcast.cluster.Cluster");
        IMPORT_MAPPINGS.put("com.hazelcast.core.IMap", "com.hazelcast.map.IMap");
        IMPORT_MAPPINGS.put("com.hazelcast.core.MapEvent", "com.hazelcast.map.MapEvent");
        IMPORT_MAPPINGS.put("com.hazelcast.core.MembershipEvent", "com.hazelcast.cluster.MembershipEvent");
        IMPORT_MAPPINGS.put("com.hazelcast.core.MembershipListener", "com.hazelcast.cluster.MembershipListener");
        IMPORT_MAPPINGS.put("com.hazelcast.core.MemberAttributeEvent", "com.hazelcast.cluster.MembershipEvent");
        IMPORT_MAPPINGS.put("com.hazelcast.monitor.LocalMapStats", "com.hazelcast.map.LocalMapStats");
        IMPORT_MAPPINGS.put("com.hazelcast.config.MaxSizeConfig", "com.hazelcast.config.EvictionConfig");
        
        // Type name mappings for references in code
        TYPE_MAPPINGS.put("MemberAttributeEvent", "MembershipEvent");
        TYPE_MAPPINGS.put("MaxSizeConfig", "EvictionConfig");
        TYPE_MAPPINGS.put("MaxSizeConfig.MaxSizePolicy", "MaxSizePolicy");
        
        // Method name mappings
        METHOD_MAPPINGS.put("getStringAttribute", "getAttribute");
        METHOD_MAPPINGS.put("setMaxSizeConfig", "setEvictionConfig");
        
        // This pattern can be extended for other dependency migrations:
        // IMPORT_MAPPINGS.put("old.package.ClassName", "new.package.ClassName");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("Example: java -cp target/classes github.chains.Main /path/to/project/src");
            System.err.println("\nThis tool applies generic import transformations for breaking API changes.");
            return;
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming imports in source directory: " + sourceDir);
        System.out.println("Applying " + IMPORT_MAPPINGS.size() + " import mappings\n");
        
        try {
            Path sourcePath = Paths.get(sourceDir);
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                System.err.println("Error: Source directory does not exist: " + sourceDir);
                return;
            }
            
            // Find all Java files
            System.out.println("Searching for Java files...");
            var javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files\n");
            
            int modifiedFiles = 0;
            JavaParser parser = new JavaParser();
            
            for (Path javaFile : javaFiles) {
                try {
                    System.out.println("Processing: " + sourcePath.relativize(javaFile));
                    
                    // Parse the file
                    var parseResult = parser.parse(javaFile);
                    if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                        System.err.println("  Warning: Failed to parse, skipping");
                        continue;
                    }
                    
                    CompilationUnit cu = parseResult.getResult().get();
                    boolean importModified = transformImports(cu);
                    boolean typeModified = transformTypeReferences(cu);
                    boolean methodModified = transformMethodCalls(cu);
                    boolean constructorModified = transformConstructorCalls(cu);
                    
                    boolean modified = importModified || typeModified || methodModified || constructorModified;
                    
                    if (modified) {
                        // Write back the modified file
                        Files.write(javaFile, cu.toString().getBytes());
                        modifiedFiles++;
                        System.out.println("  ✓ Modified");
                    } else {
                        System.out.println("  No changes needed");
                    }
                    
                } catch (Exception e) {
                    System.err.println("  Error processing file: " + e.getMessage());
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("  Total files processed: " + javaFiles.size());
            System.out.println("  Files modified: " + modifiedFiles);
            System.out.println("  Import mappings applied: " + IMPORT_MAPPINGS.size());
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean transformImports(CompilationUnit cu) {
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (int i = 0; i < imports.size(); i++) {
            ImportDeclaration importDecl = imports.get(i);
            String importName = importDecl.getNameAsString();
            
            // Check for exact import matches
            if (IMPORT_MAPPINGS.containsKey(importName)) {
                String newImport = IMPORT_MAPPINGS.get(importName);
                System.out.println("    Changing import: " + importName + " -> " + newImport);
                importDecl.setName(newImport);
                modified = true;
            }
            
            // Handle wildcard imports that might contain classes we're replacing
            if (importName.endsWith(".*")) {
                String packageName = importName.substring(0, importName.length() - 2);
                
                // Check if any mapped class is from this package
                boolean hasMappedClass = IMPORT_MAPPINGS.keySet().stream()
                    .anyMatch(oldImport -> oldImport.startsWith(packageName + "."));
                
                if (hasMappedClass) {
                    System.out.println("    Warning: Wildcard import '" + importName + "' may contain classes that need updating.");
                    System.out.println("    Consider replacing wildcard imports with explicit imports for better accuracy.");
                }
            }
        }
        
        return modified;
    }
    
    private static boolean transformTypeReferences(CompilationUnit cu) {
        class TypeReferenceVisitor extends ModifierVisitor<Void> {
            boolean modified = false;
            
            @Override
            public Visitable visit(ClassOrInterfaceType type, Void arg) {
                String typeName = type.getNameAsString();
                
                if (TYPE_MAPPINGS.containsKey(typeName)) {
                    String newTypeName = TYPE_MAPPINGS.get(typeName);
                    System.out.println("    Changing type reference: " + typeName + " -> " + newTypeName);
                    type.setName(newTypeName);
                    modified = true;
                }
                
                return super.visit(type, arg);
            }
        }
        
        TypeReferenceVisitor visitor = new TypeReferenceVisitor();
        cu.accept(visitor, null);
        return visitor.modified;
    }
    
    private static boolean transformMethodCalls(CompilationUnit cu) {
        class MethodCallVisitor extends ModifierVisitor<Void> {
            boolean modified = false;
            
            @Override
            public Visitable visit(MethodCallExpr methodCall, Void arg) {
                String methodName = methodCall.getNameAsString();
                
                if (METHOD_MAPPINGS.containsKey(methodName)) {
                    String newMethodName = METHOD_MAPPINGS.get(methodName);
                    System.out.println("    Changing method call: " + methodName + "() -> " + newMethodName + "()");
                    methodCall.setName(newMethodName);
                    modified = true;
                }
                
                return super.visit(methodCall, arg);
            }
        }
        
        MethodCallVisitor visitor = new MethodCallVisitor();
        cu.accept(visitor, null);
        return visitor.modified;
    }
    
    private static boolean transformConstructorCalls(CompilationUnit cu) {
        class ConstructorVisitor extends ModifierVisitor<Void> {
            boolean modified = false;
            
            @Override
            public Visitable visit(ObjectCreationExpr expr, Void arg) {
                String typeName = expr.getTypeAsString();
                
                if (TYPE_MAPPINGS.containsKey(typeName)) {
                    String newTypeName = TYPE_MAPPINGS.get(typeName);
                    System.out.println("    Changing constructor call: new " + typeName + "() -> new " + newTypeName + "()");
                    expr.getType().setName(newTypeName);
                    modified = true;
                }
                
                // Also handle nested types like MaxSizeConfig.MaxSizePolicy
                if (typeName.contains(".")) {
                    String[] parts = typeName.split("\\.");
                    if (parts.length > 0 && TYPE_MAPPINGS.containsKey(parts[0])) {
                        String newBaseType = TYPE_MAPPINGS.get(parts[0]);
                        String newTypeName = newBaseType;
                        if (parts.length > 1) {
                            newTypeName = newBaseType + "." + parts[1];
                        }
                        System.out.println("    Changing constructor call: new " + typeName + "() -> new " + newTypeName + "()");
                        expr.getType().setName(newTypeName);
                        modified = true;
                    }
                }
                
                return super.visit(expr, arg);
            }
        }
        
        ConstructorVisitor visitor = new ConstructorVisitor();
        cu.accept(visitor, null);
        return visitor.modified;
    }
}