package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar javaparser.jar <fully-qualified-type> <source-directory>");
            System.err.println("Example: java -jar javaparser.jar org.snmp4j.agent.ManagedObject /path/to/project/src");
            System.exit(1);
        }
        
        String fullyQualifiedType = args[0];
        String sourceDir = args[1];
        
        System.out.println("Transforming raw type references for: " + fullyQualifiedType);
        System.out.println("Source directory: " + sourceDir);
        
        try {
            transformProject(fullyQualifiedType, sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String fullyQualifiedType, String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist or is not a directory: " + sourceDir);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(fullyQualifiedType, javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files.");
    }
    
    private static boolean processFile(String fullyQualifiedType, Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            cu = parser.parse(in).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + filePath));
        }
        
        RawTypeVisitor visitor = new RawTypeVisitor(fullyQualifiedType);
        boolean modified = visitor.visit(cu, null) != null;
        
        if (modified) {
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            System.out.println("  Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    private static class RawTypeVisitor extends ModifierVisitor<Void> {
        private final String targetType;
        private final String simpleName;
        
        public RawTypeVisitor(String fullyQualifiedType) {
            this.targetType = fullyQualifiedType;
            this.simpleName = fullyQualifiedType.substring(fullyQualifiedType.lastIndexOf('.') + 1);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType type, Void arg) {
            Type visitedType = (Type) super.visit(type, arg);
            
            if (!(visitedType instanceof ClassOrInterfaceType)) {
                return visitedType;
            }
            
            ClassOrInterfaceType ciType = (ClassOrInterfaceType) visitedType;
            
            // Check if this is our target type (either simple name or fully qualified)
            boolean isTargetType = ciType.getNameAsString().equals(simpleName);
            
            if (isTargetType && ciType.getTypeArguments().isEmpty()) {
                // Don't modify if this is in an extends/implements clause of a class declaration
                // because the class might be implementing the interface with raw methods
                Node parent = ciType.getParentNode().orElse(null);
                if (parent instanceof ClassOrInterfaceDeclaration) {
                    ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) parent;
                    if (classDecl.getImplementedTypes().contains(ciType) || 
                        classDecl.getExtendedTypes().contains(ciType)) {
                        // Skip extends/implements clauses
                        return ciType;
                    }
                }
                
                // For all other cases (variable declarations, generic parameters, etc.)
                // Add <?> wildcard
                NodeList<Type> typeArgs = new NodeList<>();
                typeArgs.add(new WildcardType());
                ciType.setTypeArguments(typeArgs);
                
                System.out.println("    Added <?> to " + simpleName + " at line " + 
                    ciType.getRange().map(r -> r.begin.line).orElse(-1));
                return ciType;
            }
            
            return ciType;
        }
    }
}