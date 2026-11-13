package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * Generic JavaParser transformation to fix Jackson 2.13.4.1 StreamWriteException breaking change.
 * This transformation replaces references to com.fasterxml.jackson.core.exc.StreamWriteException
 * with java.io.IOException in catch clauses, throws declarations, and imports.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        // Input directory (can be parameterized)
        String inputDir = args.length > 0 ? args[0] : "/workspace/OCR4all";
        
        // Process all Java files in the directory
        Files.walk(Paths.get(inputDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations
            TransformVisitor visitor = new TransformVisitor();
            visitor.visit(cu, null);
            
            // Print the modified AST to the file
            String modifiedCode = cu.toString();
            Files.write(filePath, modifiedCode.getBytes());
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that performs the transformation on Jackson StreamWriteException references
     */
    private static class TransformVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            
            // Handle method signature - replace throws clause if it contains StreamWriteException
            md.getThrownExceptions().removeIf(type -> {
                if (type instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
                    String typeName = classType.getNameAsString();
                    String qualifiedName = classType.asString();
                    
                    // Check if this is StreamWriteException from the problematic package
                    if ("StreamWriteException".equals(typeName) && 
                        qualifiedName.contains("com.fasterxml.jackson.core.exc")) {
                        return true;
                    }
                }
                return false;
            });
            
            // Add IOException if not already present
            boolean hasIOException = md.getThrownExceptions().stream()
                .anyMatch(type -> type instanceof ClassOrInterfaceType &&
                    "IOException".equals(((ClassOrInterfaceType) type).getNameAsString()));
            
            if (!hasIOException) {
                // Create ReferenceType for IOException
                ClassOrInterfaceType ioExceptionType = new ClassOrInterfaceType(null, "IOException");
                md.addThrownException(ioExceptionType);
            }
        }
        
        @Override
        public void visit(TryStmt ts, Void arg) {
            super.visit(ts, arg);
            
            // Process catch clauses
            ts.getCatchClauses().forEach(catchClause -> {
                // Replace StreamWriteException with IOException in catch clause
                if (catchClause.getParameter().getType() instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType paramType = (ClassOrInterfaceType) catchClause.getParameter().getType();
                    String typeName = paramType.getNameAsString();
                    String qualifiedName = paramType.asString();
                    
                    if ("StreamWriteException".equals(typeName) && 
                        qualifiedName.contains("com.fasterxml.jackson.core.exc")) {
                        
                        // Replace with IOException
                        ClassOrInterfaceType ioExceptionType = new ClassOrInterfaceType(null, "IOException");
                        catchClause.getParameter().setType(ioExceptionType);
                    }
                }
            });
        }
        
        @Override
        public void visit(ImportDeclaration id, Void arg) {
            super.visit(id, arg);
            
            // Remove import statements for StreamWriteException
            String importName = id.getNameAsString();
            if (importName.contains("StreamWriteException") && 
                importName.contains("com.fasterxml.jackson.core.exc")) {
                id.remove();
            }
        }
    }
}
