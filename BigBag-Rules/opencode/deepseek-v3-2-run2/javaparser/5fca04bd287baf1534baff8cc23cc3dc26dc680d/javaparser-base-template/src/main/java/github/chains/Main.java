package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
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
        }
    }
    
    private static boolean transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            CactoosHexBytesTransformer transformer = new CactoosHexBytesTransformer();
            CompilationUnit transformed = (CompilationUnit) cu.accept(transformer, null);
            
            if (transformer.wasTransformed()) {
                Files.writeString(filePath, transformed.toString());
                System.out.println("Transformed: " + filePath);
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
        
        return false;
    }
    
    private static class CactoosHexBytesTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Look for pattern: new HexOf(new BytesOf(byteArray)).asString()
            if (n.getNameAsString().equals("asString")) {
                Node parent = n.getParentNode().orElse(null);
                if (parent instanceof ObjectCreationExpr) {
                    ObjectCreationExpr hexOfCreation = (ObjectCreationExpr) parent;
                    if (hexOfCreation.getTypeAsString().equals("HexOf")) {
                        if (hexOfCreation.getArguments().size() == 1) {
                            Expression hexOfArg = hexOfCreation.getArguments().get(0);
                            if (hexOfArg instanceof ObjectCreationExpr) {
                                ObjectCreationExpr bytesOfCreation = (ObjectCreationExpr) hexOfArg;
                                if (bytesOfCreation.getTypeAsString().equals("BytesOf")) {
                                    if (bytesOfCreation.getArguments().size() == 1) {
                                        // Found the pattern: new HexOf(new BytesOf(byteArray)).asString()
                                        Expression byteArrayExpr = bytesOfCreation.getArguments().get(0);
                                        
                                        // Replace with: java.util.HexFormat.of().formatHex(byteArray)
                                        MethodCallExpr hexFormatCall = new MethodCallExpr(
                                            new MethodCallExpr(
                                                new NameExpr("java.util.HexFormat"),
                                                "of"
                                            ),
                                            "formatHex",
                                            new NodeList<>(byteArrayExpr)
                                        );
                                        
                                        transformed = true;
                                        return hexFormatCall;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            // Remove imports for HexOf and BytesOf
            String importName = n.getNameAsString();
            if (importName.equals("org.cactoos.io.BytesOf") || 
                importName.equals("org.cactoos.text.HexOf")) {
                transformed = true;
                return null; // Remove the import
            }
            
            return super.visit(n, arg);
        }
    }
}