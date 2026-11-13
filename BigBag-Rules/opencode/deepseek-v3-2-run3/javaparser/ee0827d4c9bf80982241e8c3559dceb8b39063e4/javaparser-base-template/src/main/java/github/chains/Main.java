package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            Files.walkFileTree(sourceDir, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        processJavaFile(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.println("Failed to visit file: " + file);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                return;
            }
            
            boolean modified = false;
            
            // Transform imports
            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.equals("org.apache.commons.io.input.BoundedInputStream") ||
                    importName.equals("org.apache.commons.io.input.ClosedInputStream") ||
                    importName.equals("org.apache.commons.io.output.ThresholdingOutputStream") ||
                    importName.equals("org.apache.commons.io.output.NullPrintStream")) {
                    importsToRemove.add(importDecl);
                    modified = true;
                }
            }
            cu.getImports().removeAll(importsToRemove);
            
            // Add visitor to transform code
            CommonsIOTransformer transformer = new CommonsIOTransformer();
            transformer.visit(cu, null);
            modified = modified || transformer.wasModified();
            
            if (modified) {
                Files.write(javaFile, cu.toString().getBytes());
                System.out.println("Annotated: " + javaFile);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
        }
    }
    
    private static class CommonsIOTransformer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            String typeName = n.getType().asString();
            if (typeName.equals("BoundedInputStream")) {
                // Add TODO comment before this line
                com.github.javaparser.ast.Node parent = n.getParentNode().orElse(null);
                if (parent != null) {
                    parent.setComment(new com.github.javaparser.ast.comments.LineComment(
                        "TODO: BoundedInputStream was removed in commons-io 20030203.000550. " +
                        "Need to implement bounded stream functionality."));
                }
                modified = true;
            } else if (typeName.equals("ClosedInputStream")) {
                // Add TODO comment
                com.github.javaparser.ast.Node parent = n.getParentNode().orElse(null);
                if (parent != null) {
                    parent.setComment(new com.github.javaparser.ast.comments.LineComment(
                        "TODO: ClosedInputStream was removed. Replace with ByteArrayInputStream(new byte[0])"));
                }
                modified = true;
            } else if (typeName.equals("NullPrintStream")) {
                // Add TODO comment
                com.github.javaparser.ast.Node parent = n.getParentNode().orElse(null);
                if (parent != null) {
                    parent.setComment(new com.github.javaparser.ast.comments.LineComment(
                        "TODO: NullPrintStream was removed. Use System.out or custom null stream."));
                }
                modified = true;
            }
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            if (n.getNameAsString().equals("getByteCount")) {
                // Add TODO comment about type change
                com.github.javaparser.ast.Node parent = n.getParentNode().orElse(null);
                if (parent != null) {
                    parent.setComment(new com.github.javaparser.ast.comments.LineComment(
                        "TODO: getByteCount() returns long, getCount() returns int. Check for overflow."));
                }
                // Still change the method name
                n.setName("getCount");
                modified = true;
            }
        }
        
        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            super.visit(n, arg);
            
            if (n.getNameAsString().equals("NULL_PRINT_STREAM")) {
                // Add TODO comment
                com.github.javaparser.ast.Node parent = n.getParentNode().orElse(null);
                if (parent != null) {
                    parent.setComment(new com.github.javaparser.ast.comments.LineComment(
                        "TODO: NullPrintStream.NULL_PRINT_STREAM was removed."));
                }
                modified = true;
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            super.visit(n, arg);
            
            if (n.getNameAsString().equals("ThresholdingOutputStream")) {
                // Add TODO comment
                com.github.javaparser.ast.Node parent = n.getParentNode().orElse(null);
                if (parent != null) {
                    parent.setComment(new com.github.javaparser.ast.comments.LineComment(
                        "TODO: ThresholdingOutputStream was removed. Class needs major rewrite."));
                }
                modified = true;
            }
        }
    }
}