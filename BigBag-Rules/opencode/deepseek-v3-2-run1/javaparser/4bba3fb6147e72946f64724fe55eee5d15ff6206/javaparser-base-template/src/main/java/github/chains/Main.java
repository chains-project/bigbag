package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        
        try {
            transformDirectory(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(String sourceDir) throws Exception {
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to transform.");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            if (transformFile(parser, javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files.");
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean transformFile(JavaParser parser, Path javaFile) throws Exception {
        CompilationUnit cu;
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            cu = parser.parse(in).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse: " + javaFile));
        }
        
        boolean modified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.findAll(ImportDeclaration.class)) {
            String importName = importDecl.getNameAsString();
            
            // Check for javax.annotation imports
            if (importName.startsWith("javax.annotation")) {
                String newImportName = importName.replace("javax.annotation", "jakarta.annotation");
                importDecl.setName(newImportName);
                modified = true;
                System.out.println("  Updated import: " + importName + " -> " + newImportName);
            }
        }
        
        // Transform annotation expressions that might contain javax.annotation references
        for (AnnotationExpr annotation : cu.findAll(AnnotationExpr.class)) {
            String annotationName = annotation.getNameAsString();
            
            // Check for fully-qualified javax.annotation annotations
            if (annotationName.startsWith("javax.annotation.")) {
                String newName = annotationName.replace("javax.annotation", "jakarta.annotation");
                annotation.setName(newName);
                modified = true;
                System.out.println("  Updated annotation: " + annotationName + " -> " + newName);
            }
            
            // Also check if the annotation name is a Name with scope
            Node parent = annotation.getParentNode().orElse(null);
            if (annotation.getName() instanceof Name) {
                Name name = (Name) annotation.getName();
                String nameAsString = name.asString();
                if (nameAsString.startsWith("javax.annotation.")) {
                    String newNameStr = nameAsString.replace("javax.annotation", "jakarta.annotation");
                    name.setIdentifier(newNameStr.substring(newNameStr.lastIndexOf('.') + 1));
                    if (name.getQualifier().isPresent()) {
                        String qualifier = name.getQualifier().get().asString();
                        if (qualifier.startsWith("javax.annotation")) {
                            String newQualifier = qualifier.replace("javax.annotation", "jakarta.annotation");
                            name.getQualifier().get().setIdentifier(newQualifier.substring(newQualifier.lastIndexOf('.') + 1));
                            if (name.getQualifier().get().getQualifier().isPresent()) {
                                // Handle deeper nesting if needed
                                Name qualifierName = name.getQualifier().get();
                                String fullQualifier = getFullName(qualifierName);
                                if (fullQualifier.startsWith("javax.annotation")) {
                                    String newFullQualifier = fullQualifier.replace("javax.annotation", "jakarta.annotation");
                                    // This is complex - for now, we'll rely on imports being fixed
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Transform type references
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String typeName = type.getNameAsString();
            
            // Check for javax.annotation in type names
            if (typeName.startsWith("javax.annotation.")) {
                String newTypeName = typeName.replace("javax.annotation", "jakarta.annotation");
                type.setName(newTypeName);
                modified = true;
                System.out.println("  Updated type: " + typeName + " -> " + newTypeName);
            }
            
            // Check scopes
            if (type.getScope().isPresent()) {
                String scopeName = type.getScope().get().asString();
                if (scopeName.startsWith("javax.annotation")) {
                    String newScopeName = scopeName.replace("javax.annotation", "jakarta.annotation");
                    type.getScope().get().setName(newScopeName);
                    modified = true;
                    System.out.println("  Updated type scope: " + scopeName + " -> " + newScopeName);
                }
            }
        }
        
        if (modified) {
            // Write back the transformed file
            String transformedCode = cu.toString();
            Files.write(javaFile, transformedCode.getBytes());
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    private static String getFullName(Name name) {
        StringBuilder sb = new StringBuilder();
        if (name.getQualifier().isPresent()) {
            sb.append(getFullName(name.getQualifier().get()));
            sb.append(".");
        }
        sb.append(name.getIdentifier());
        return sb.toString();
    }
}
