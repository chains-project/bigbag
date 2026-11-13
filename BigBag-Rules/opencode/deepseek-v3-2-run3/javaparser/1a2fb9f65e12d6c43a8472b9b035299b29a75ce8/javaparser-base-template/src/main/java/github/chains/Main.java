package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws Exception {
        List<Path> javaFiles = Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser javaParser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile)
                );
                
                boolean modified = transformCompilationUnit(cu);
                
                if (modified) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files out of " + javaFiles.size());
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        // Transform import declarations
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.validation")) {
                String newImportName = importName.replaceFirst("^javax\\.validation", "jakarta.validation");
                importDecl.setName(new Name(newImportName));
                modified[0] = true;
                System.out.println("  Import transformed: " + importName + " -> " + newImportName);
            }
        }
        
        // Transform type references in the code using a visitor
        cu.accept(new com.github.javaparser.ast.visitor.ModifierVisitor<Void>() {
            @Override
            public Node visit(NameExpr n, Void arg) {
                NameExpr visited = (NameExpr) super.visit(n, arg);
                String nameStr = visited.getNameAsString();
                
                if (nameStr.startsWith("javax.validation.")) {
                    String newNameStr = nameStr.replaceFirst("^javax\\.validation", "jakarta.validation");
                    visited.setName(newNameStr);
                    modified[0] = true;
                    System.out.println("  Name expression transformed: " + nameStr + " -> " + newNameStr);
                }
                
                return visited;
            }
            
            @Override
            public Node visit(Name n, Void arg) {
                Name visited = (Name) super.visit(n, arg);
                String nameStr = visited.asString();
                
                if (nameStr.startsWith("javax.validation.")) {
                    String newNameStr = nameStr.replaceFirst("^javax\\.validation", "jakarta.validation");
                    visited.setIdentifier(newNameStr);
                    modified[0] = true;
                    System.out.println("  Name transformed: " + nameStr + " -> " + newNameStr);
                }
                
                return visited;
            }
            
            @Override
            public Node visit(ClassOrInterfaceType n, Void arg) {
                ClassOrInterfaceType visited = (ClassOrInterfaceType) super.visit(n, arg);
                
                // Handle both simple and qualified names
                String typeName = visited.getNameAsString();
                
                // Check if type name itself contains javax.validation
                if (typeName.startsWith("javax.validation.")) {
                    String newTypeName = typeName.replaceFirst("^javax\\.validation", "jakarta.validation");
                    visited.setName(newTypeName);
                    modified[0] = true;
                    System.out.println("  Class/interface type transformed: " + typeName + " -> " + newTypeName);
                }
                
                // Handle scoped names (e.g., javax.validation.Validator)
                if (visited.getScope().isPresent()) {
                    Node scope = visited.getScope().get();
                    if (scope instanceof Name) {
                        Name scopeName = (Name) scope;
                        String scopeStr = scopeName.asString();
                        if (scopeStr.startsWith("javax.validation")) {
                            String newScopeStr = scopeStr.replaceFirst("^javax\\.validation", "jakarta.validation");
                            scopeName.setIdentifier(newScopeStr);
                            modified[0] = true;
                            System.out.println("  Scoped type transformed: " + scopeStr + "." + typeName + " -> " + newScopeStr + "." + typeName);
                        }
                    }
                }
                
                return visited;
            }
        }, null);
        
        return modified[0];
    }
}