package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for logback-core 1.2.9 breaking API change.
 * 
 * Breaking Change: Encoder interface now requires concrete implementations of 
 * headerBytes() and footerBytes() methods. EncoderBase no longer provides 
 * default implementations.
 * 
 * Transformation: Add headerBytes() and footerBytes() methods returning null 
 * to any class extending EncoderBase.
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String ENCODER_BASE_CLASS = "ch.qos.logback.core.encoder.EncoderBase";
    private static final String HEADER_BYTES_METHOD = "headerBytes";
    private static final String FOOTER_BYTES_METHOD = "footerBytes";
    
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
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Transformation complete. Modified " + modifiedFiles + " files.");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        EncoderBaseTransformer transformer = new EncoderBaseTransformer();
        Visitable result = transformer.visit(cu, null);
        
        if (transformer.wasModified()) {
            String transformedContent = result.toString();
            Files.write(filePath, transformedContent.getBytes());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    private static class EncoderBaseTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Check if this class extends EncoderBase
            if (n.getExtendedTypes().stream()
                    .anyMatch(type -> type.getNameAsString().equals("EncoderBase") || 
                                     type.getNameAsString().equals(ENCODER_BASE_CLASS))) {
                
                // Check if headerBytes method is missing
                boolean hasHeaderBytes = n.getMethods().stream()
                        .anyMatch(method -> method.getNameAsString().equals(HEADER_BYTES_METHOD));
                
                // Check if footerBytes method is missing
                boolean hasFooterBytes = n.getMethods().stream()
                        .anyMatch(method -> method.getNameAsString().equals(FOOTER_BYTES_METHOD));
                
                // Add missing methods
                if (!hasHeaderBytes) {
                    MethodDeclaration headerMethod = createHeaderBytesMethod();
                    n.addMember(headerMethod);
                    modified = true;
                }
                
                if (!hasFooterBytes) {
                    MethodDeclaration footerMethod = createFooterBytesMethod();
                    n.addMember(footerMethod);
                    modified = true;
                }
            }
            
            return super.visit(n, arg);
        }
        
        private MethodDeclaration createHeaderBytesMethod() {
            return new MethodDeclaration()
                    .setPublic(true)
                    .setType("byte[]")
                    .setName(HEADER_BYTES_METHOD)
                    .setBody(
                        new com.github.javaparser.ast.stmt.BlockStmt()
                            .addStatement("return null;")
                    );
        }
        
        private MethodDeclaration createFooterBytesMethod() {
            return new MethodDeclaration()
                    .setPublic(true)
                    .setType("byte[]")
                    .setName(FOOTER_BYTES_METHOD)
                    .setBody(
                        new com.github.javaparser.ast.stmt.BlockStmt()
                            .addStatement("return null;")
                    );
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}