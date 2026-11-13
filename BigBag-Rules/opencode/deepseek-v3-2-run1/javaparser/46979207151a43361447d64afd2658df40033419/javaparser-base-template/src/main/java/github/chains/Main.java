package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Mapping of old interface names to new interface names
    private static final String[][] INTERFACE_MAPPINGS = {
        {"org.jvnet.jaxb2_commons.lang.ToString", "org.jvnet.jaxb2_commons.lang.ToString2"},
        {"org.jvnet.jaxb2_commons.lang.Equals", "org.jvnet.jaxb2_commons.lang.Equals2"},
        {"org.jvnet.jaxb2_commons.lang.HashCode", "org.jvnet.jaxb2_commons.lang.HashCode2"},
        {"org.jvnet.jaxb2_commons.lang.CopyTo", "org.jvnet.jaxb2_commons.lang.CopyTo2"},
        {"org.jvnet.jaxb2_commons.lang.MergeFrom", "org.jvnet.jaxb2_commons.lang.MergeFrom2"},
        {"org.jvnet.jaxb2_commons.lang.ToStringStrategy", "org.jvnet.jaxb2_commons.lang.ToStringStrategy2"},
        {"org.jvnet.jaxb2_commons.lang.EqualsStrategy", "org.jvnet.jaxb2_commons.lang.EqualsStrategy2"},
        {"org.jvnet.jaxb2_commons.lang.HashCodeStrategy", "org.jvnet.jaxb2_commons.lang.HashCodeStrategy2"},
        {"org.jvnet.jaxb2_commons.lang.CopyStrategy", "org.jvnet.jaxb2_commons.lang.CopyStrategy2"},
        {"org.jvnet.jaxb2_commons.lang.MergeStrategy", "org.jvnet.jaxb2_commons.lang.MergeStrategy2"}
    };
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        
        try {
            transformDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(File directory) throws Exception {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory.getPath());
        }
        
        List<File> javaFiles = findJavaFiles(directory);
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        for (File javaFile : javaFiles) {
            transformFile(javaFile);
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
    
    private static void transformFile(File javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse: " + javaFile.getPath())
        );
        
        final boolean[] modifiedHolder = new boolean[1];
        modifiedHolder[0] = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            for (String[] mapping : INTERFACE_MAPPINGS) {
                if (importName.equals(mapping[0])) {
                    importDecl.setName(mapping[1]);
                    modifiedHolder[0] = true;
                    System.out.println("Updated import in " + javaFile.getName() + 
                                     ": " + mapping[0] + " -> " + mapping[1]);
                }
            }
        }
        
        // Transform class implements clauses and type references
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                super.visit(n, arg);
                
                // Check implemented interfaces
                NodeList<ClassOrInterfaceType> implementedTypes = n.getImplementedTypes();
                for (ClassOrInterfaceType type : implementedTypes) {
                    String typeName = type.getNameAsString();
                    String fullTypeName = resolveFullTypeName(type, cu);
                    
                    for (String[] mapping : INTERFACE_MAPPINGS) {
                        String oldSimpleName = getSimpleName(mapping[0]);
                        String newSimpleName = getSimpleName(mapping[1]);
                        
                        if (typeName.equals(oldSimpleName) || 
                            (fullTypeName != null && fullTypeName.equals(mapping[0]))) {
                            type.setName(newSimpleName);
                            modifiedHolder[0] = true;
                            System.out.println("Updated implements in " + javaFile.getName() + 
                                             ": " + oldSimpleName + " -> " + newSimpleName);
                        }
                    }
                }
                
                // Check extended types
                NodeList<ClassOrInterfaceType> extendedTypes = n.getExtendedTypes();
                for (ClassOrInterfaceType type : extendedTypes) {
                    String typeName = type.getNameAsString();
                    String fullTypeName = resolveFullTypeName(type, cu);
                    
                    for (String[] mapping : INTERFACE_MAPPINGS) {
                        String oldSimpleName = getSimpleName(mapping[0]);
                        String newSimpleName = getSimpleName(mapping[1]);
                        
                        if (typeName.equals(oldSimpleName) || 
                            (fullTypeName != null && fullTypeName.equals(mapping[0]))) {
                            type.setName(newSimpleName);
                            modifiedHolder[0] = true;
                            System.out.println("Updated extends in " + javaFile.getName() + 
                                             ": " + oldSimpleName + " -> " + newSimpleName);
                        }
                    }
                }
            }
            
            @Override
            public void visit(ClassOrInterfaceType n, Void arg) {
                super.visit(n, arg);
                
                String typeName = n.getNameAsString();
                String fullTypeName = resolveFullTypeName(n, cu);
                
                for (String[] mapping : INTERFACE_MAPPINGS) {
                    String oldSimpleName = getSimpleName(mapping[0]);
                    String newSimpleName = getSimpleName(mapping[1]);
                    
                    if (typeName.equals(oldSimpleName) || 
                        (fullTypeName != null && fullTypeName.equals(mapping[0]))) {
                        n.setName(newSimpleName);
                        modifiedHolder[0] = true;
                    }
                }
            }
            
            @Override
            public void visit(MethodDeclaration n, Void arg) {
                super.visit(n, arg);
                
                // Transform return types
                Type returnType = n.getType();
                if (returnType.isClassOrInterfaceType()) {
                    ClassOrInterfaceType coit = returnType.asClassOrInterfaceType();
                    String typeName = coit.getNameAsString();
                    String fullTypeName = resolveFullTypeName(coit, cu);
                    
                    for (String[] mapping : INTERFACE_MAPPINGS) {
                        String oldSimpleName = getSimpleName(mapping[0]);
                        String newSimpleName = getSimpleName(mapping[1]);
                        
                        if (typeName.equals(oldSimpleName) || 
                            (fullTypeName != null && fullTypeName.equals(mapping[0]))) {
                            coit.setName(newSimpleName);
                            modifiedHolder[0] = true;
                        }
                    }
                }
                
                // Transform parameter types
                n.getParameters().forEach(param -> {
                    Type paramType = param.getType();
                    if (paramType.isClassOrInterfaceType()) {
                        ClassOrInterfaceType coit = paramType.asClassOrInterfaceType();
                        String typeName = coit.getNameAsString();
                        String fullTypeName = resolveFullTypeName(coit, cu);
                        
                        for (String[] mapping : INTERFACE_MAPPINGS) {
                            String oldSimpleName = getSimpleName(mapping[0]);
                            String newSimpleName = getSimpleName(mapping[1]);
                            
                            if (typeName.equals(oldSimpleName) || 
                                (fullTypeName != null && fullTypeName.equals(mapping[0]))) {
                                coit.setName(newSimpleName);
                                modifiedHolder[0] = true;
                            }
                        }
                    }
                });
            }
        }, null);
        
        if (modifiedHolder[0]) {
            // Write back the transformed file
            try (FileWriter writer = new FileWriter(javaFile)) {
                writer.write(cu.toString());
            }
            System.out.println("Updated file: " + javaFile.getPath());
        }
    }
    
    private static String getSimpleName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }
    
    private static String resolveFullTypeName(ClassOrInterfaceType type, CompilationUnit cu) {
        String typeName = type.getNameAsString();
        
        // Check if it's already a fully qualified name (contains dot)
        if (typeName.contains(".")) {
            return typeName;
        }
        
        // Check imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.isAsterisk()) {
                // Can't resolve wildcard imports
                continue;
            }
            
            String importName = importDecl.getNameAsString();
            String importedSimpleName = getSimpleName(importName);
            
            if (importedSimpleName.equals(typeName)) {
                return importName;
            }
        }
        
        // Check if it's in the same package
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");
            
        if (!packageName.isEmpty()) {
            // Check if the type might be in org.jvnet.jaxb2_commons.lang package
            // based on common patterns
            for (String[] mapping : INTERFACE_MAPPINGS) {
                String simpleName = getSimpleName(mapping[0]);
                if (simpleName.equals(typeName)) {
                    return mapping[0];
                }
            }
        }
        
        return null;
    }
}