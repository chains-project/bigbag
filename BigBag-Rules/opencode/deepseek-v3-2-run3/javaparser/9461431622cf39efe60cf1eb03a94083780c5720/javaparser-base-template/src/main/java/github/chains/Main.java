package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            return;
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void transformProject(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(() -> 
                    new RuntimeException("Failed to parse: " + javaFile));
                
                TransformationResult result = transformCompilationUnit(cu);
                
                if (result.modified) {
                    transformedFiles++;
                    // Write back the transformed file
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Failed to process file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("Total files transformed: " + transformedFiles);
    }
    
    private static TransformationResult transformCompilationUnit(CompilationUnit cu) {
        ManagedObjectTypeVisitor visitor = new ManagedObjectTypeVisitor();
        cu.accept(visitor, null);
        return visitor.result;
    }
    
    private static class TransformationResult {
        boolean modified = false;
    }
    
    private static class ManagedObjectTypeVisitor extends ModifierVisitor<TransformationResult> {
        private TransformationResult result = new TransformationResult();
        
        @Override
        public Visitable visit(ClassOrInterfaceType type, TransformationResult arg) {
            String typeName = type.getNameAsString();
            
            if ("ManagedObject".equals(typeName)) {
                // Check if it already has type arguments
                if (!type.getTypeArguments().isPresent()) {
                    // Add wildcard <?> to ManagedObject type references
                    type.setTypeArguments(new NodeList<>(new WildcardType()));
                    result.modified = true;
                    System.out.println("    Found ManagedObject without type arguments, added <?>");
                } else {
                    System.out.println("    Found ManagedObject with type arguments: " + type.getTypeArguments().get());
                }
            }
            
            return super.visit(type, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration declaration, TransformationResult arg) {
            // Check if this class implements ManagedObject
            if (declaration.getImplementedTypes() != null) {
                for (ClassOrInterfaceType implementedType : declaration.getImplementedTypes()) {
                    if ("ManagedObject".equals(implementedType.getNameAsString())) {
                        if (!implementedType.getTypeArguments().isPresent()) {
                            // This is a class implementing ManagedObject
                            // We need to change it to ManagedObject<SubRequest<?>>
                            // Create SubRequest<?> type
                            ClassOrInterfaceType subRequestType = new ClassOrInterfaceType();
                            subRequestType.setName("SubRequest");
                            subRequestType.setTypeArguments(new NodeList<>(new WildcardType()));
                            
                            // Set ManagedObject<SubRequest<?>>
                            implementedType.setTypeArguments(new NodeList<>(subRequestType));
                            result.modified = true;
                        }
                    }
                }
            }
            
            return super.visit(declaration, arg);
        }
    }
}