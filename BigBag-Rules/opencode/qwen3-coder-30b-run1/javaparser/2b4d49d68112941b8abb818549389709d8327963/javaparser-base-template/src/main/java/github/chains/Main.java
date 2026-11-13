package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // This is a generic transformation rule for Hazelcast 4.0.5 breaking changes
        // It fixes the following breaking changes:
        // 1. com.hazelcast.core.Member -> com.hazelcast.cluster.Member
        // 2. com.hazelcast.core.Cluster -> com.hazelcast.cluster.Cluster
        // 3. com.hazelcast.core.MemberAttributeEvent -> com.hazelcast.cluster.MemberAttributeEvent
        // 4. com.hazelcast.core.MembershipEvent -> com.hazelcast.cluster.MembershipEvent
        // 5. com.hazelcast.core.MembershipListener -> com.hazelcast.cluster.MembershipListener
        // 6. com.hazelcast.core.IMap -> com.hazelcast.map.IMap
        // 7. com.hazelcast.core.MapEvent -> com.hazelcast.map.MapEvent
        // 8. com.hazelcast.config.MaxSizeConfig -> com.hazelcast.config.MaxSizeConfig (no change needed)
        
        String sourceDir = "/workspace/openfire-hazelcast-plugin/src/java";
        String targetDir = "/workspace/openfire-hazelcast-plugin/src/java";
        
        try {
            processJavaFiles(sourceDir, targetDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processJavaFiles(String sourceDir, String targetDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);
        
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    processJavaFile(path, sourcePath, targetPath);
                } catch (Exception e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
    }
    
    private static void processJavaFile(Path sourceFile, Path sourceRoot, Path targetRoot) throws IOException {
        String content = Files.readString(sourceFile);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        boolean modified = false;
        
        // Fix imports
        modified |= fixImports(cu);
        
        // Fix class declarations
        modified |= fixClassDeclarations(cu);
        
        // Fix method signatures and variable declarations
        modified |= fixMethodDeclarations(cu);
        
        // Fix new expressions and other usages
        modified |= fixUsages(cu);
        
        if (modified) {
            // Create target directory
            Path relativePath = sourceRoot.relativize(sourceFile);
            Path targetFile = targetRoot.resolve(relativePath);
            Files.createDirectories(targetFile.getParent());
            
            // Write modified content
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(targetFile))) {
                writer.print(cu.toString());
            }
        }
    }
    
    private static boolean fixImports(CompilationUnit cu) {
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        List<ImportDeclaration> toRemove = new ArrayList<>();
        List<ImportDeclaration> toAdd = new ArrayList<>();
        
        for (ImportDeclaration imp : imports) {
            String importName = imp.getNameAsString();
            
            // Check for core package imports that need to be updated
            if (importName.startsWith("com.hazelcast.core.")) {
                // Map core package classes to their new packages
                if (importName.equals("com.hazelcast.core.Member")) {
                    toRemove.add(imp);
                    toAdd.add(new ImportDeclaration("com.hazelcast.cluster.Member", false, false));
                    modified = true;
                } else if (importName.equals("com.hazelcast.core.Cluster")) {
                    toRemove.add(imp);
                    toAdd.add(new ImportDeclaration("com.hazelcast.cluster.Cluster", false, false));
                    modified = true;
                } else if (importName.equals("com.hazelcast.core.MemberAttributeEvent")) {
                    toRemove.add(imp);
                    toAdd.add(new ImportDeclaration("com.hazelcast.cluster.MemberAttributeEvent", false, false));
                    modified = true;
                } else if (importName.equals("com.hazelcast.core.MembershipEvent")) {
                    toRemove.add(imp);
                    toAdd.add(new ImportDeclaration("com.hazelcast.cluster.MembershipEvent", false, false));
                    modified = true;
                } else if (importName.equals("com.hazelcast.core.MembershipListener")) {
                    toRemove.add(imp);
                    toAdd.add(new ImportDeclaration("com.hazelcast.cluster.MembershipListener", false, false));
                    modified = true;
                } else if (importName.equals("com.hazelcast.core.IMap")) {
                    toRemove.add(imp);
                    toAdd.add(new ImportDeclaration("com.hazelcast.map.IMap", false, false));
                    modified = true;
                } else if (importName.equals("com.hazelcast.core.MapEvent")) {
                    toRemove.add(imp);
                    toAdd.add(new ImportDeclaration("com.hazelcast.map.MapEvent", false, false));
                    modified = true;
                }
            }
        }
        
        // Remove old imports
        imports.removeAll(toRemove);
        // Add new imports
        imports.addAll(toAdd);
        
        return modified;
    }
    
    private static boolean fixClassDeclarations(CompilationUnit cu) {
        boolean modified = false;
        
        // Fix class declarations that reference old Hazelcast classes
        for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            // Check for superclass or interfaces that need to be updated
            NodeList<ClassOrInterfaceType> extendsList = classDecl.getExtendedTypes();
            for (ClassOrInterfaceType type : extendsList) {
                modified |= fixTypeReference(type);
            }
            
            NodeList<ClassOrInterfaceType> implementsList = classDecl.getImplementedTypes();
            for (ClassOrInterfaceType type : implementsList) {
                modified |= fixTypeReference(type);
            }
        }
        
        return modified;
    }
    
    private static boolean fixMethodDeclarations(CompilationUnit cu) {
        boolean modified = false;
        
        // Fix method declarations
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            // Fix return type
            if (method.getType() != null) {
                modified |= fixTypeReference(method.getType());
            }
            
            // Fix parameter types
            for (com.github.javaparser.ast.body.Parameter param : method.getParameters()) {
                modified |= fixTypeReference(param.getType());
            }
        }
        
        // Fix variable declarations
        for (VariableDeclarator var : cu.findAll(VariableDeclarator.class)) {
            if (var.getType() != null) {
                modified |= fixTypeReference(var.getType());
            }
        }
        
        return modified;
    }
    
    private static boolean fixTypeReference(Type type) {
        if (type instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
            String name = classType.getNameAsString();
            String qualifiedName = classType.asString();
            
            // Check if it's a Hazelcast core class that needs to be updated
            if (qualifiedName.equals("com.hazelcast.core.Member")) {
                classType.setName("com.hazelcast.cluster.Member");
                return true;
            } else if (qualifiedName.equals("com.hazelcast.core.Cluster")) {
                classType.setName("com.hazelcast.cluster.Cluster");
                return true;
            } else if (qualifiedName.equals("com.hazelcast.core.MemberAttributeEvent")) {
                classType.setName("com.hazelcast.cluster.MemberAttributeEvent");
                return true;
            } else if (qualifiedName.equals("com.hazelcast.core.MembershipEvent")) {
                classType.setName("com.hazelcast.cluster.MembershipEvent");
                return true;
            } else if (qualifiedName.equals("com.hazelcast.core.MembershipListener")) {
                classType.setName("com.hazelcast.cluster.MembershipListener");
                return true;
            } else if (qualifiedName.equals("com.hazelcast.core.IMap")) {
                classType.setName("com.hazelcast.map.IMap");
                return true;
            } else if (qualifiedName.equals("com.hazelcast.core.MapEvent")) {
                classType.setName("com.hazelcast.map.MapEvent");
                return true;
            }
        }
        return false;
    }
    
    private static boolean fixUsages(CompilationUnit cu) {
        boolean modified = false;
        
        // Fix new expressions and other usages that reference old classes
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if (expr.getType() != null) {
                String qualifiedName = expr.getType().asString();
                if (qualifiedName.equals("com.hazelcast.core.Member")) {
                    expr.setType("com.hazelcast.cluster.Member");
                    modified = true;
                } else if (qualifiedName.equals("com.hazelcast.core.Cluster")) {
                    expr.setType("com.hazelcast.cluster.Cluster");
                    modified = true;
                } else if (qualifiedName.equals("com.hazelcast.core.MemberAttributeEvent")) {
                    expr.setType("com.hazelcast.cluster.MemberAttributeEvent");
                    modified = true;
                } else if (qualifiedName.equals("com.hazelcast.core.MembershipEvent")) {
                    expr.setType("com.hazelcast.cluster.MembershipEvent");
                    modified = true;
                } else if (qualifiedName.equals("com.hazelcast.core.MembershipListener")) {
                    expr.setType("com.hazelcast.cluster.MembershipListener");
                    modified = true;
                } else if (qualifiedName.equals("com.hazelcast.core.IMap")) {
                    expr.setType("com.hazelcast.map.IMap");
                    modified = true;
                } else if (qualifiedName.equals("com.hazelcast.core.MapEvent")) {
                    expr.setType("com.hazelcast.map.MapEvent");
                    modified = true;
                }
            }
        }
        
        return modified;
    }
}
