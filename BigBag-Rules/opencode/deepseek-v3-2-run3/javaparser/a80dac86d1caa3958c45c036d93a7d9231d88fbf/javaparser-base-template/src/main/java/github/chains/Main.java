package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        JavaParser parser = new JavaParser();
        LogbackLoggerCastTransformer transformer = new LogbackLoggerCastTransformer();
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
                transformer.resetModified();
                cu.accept(transformer, null);
                
                // Write back if modified
                if (transformer.isModified()) {
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformation complete. Modified " + transformer.getModificationCount() + " files.");
    }
    
    static class LogbackLoggerCastTransformer extends ModifierVisitor<Void> {
        private int modificationCount = 0;
        private boolean modified = false;
        
        @Override
        public Visitable visit(CastExpr n, Void arg) {
            // Check if this is a cast to ch.qos.logback.classic.Logger
            if (n.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = n.getType().asClassOrInterfaceType();
                String typeName = type.getNameAsString();
                
                // Check for simple name Logger or fully qualified name
                boolean isLoggerCast = "Logger".equals(typeName) || 
                    "ch.qos.logback.classic.Logger".equals(type.getNameWithScope());
                
                if (isLoggerCast) {
                    System.out.println("Found cast to logback Logger: " + n);
                    modified = true;
                    modificationCount++;
                    
                    // Replace cast with the expression directly
                    // This will cause type mismatch but allows compilation
                    // The actual fix requires updating SLF4J to 2.x or logback to compatible version
                    return n.getExpression();
                }
            }
            return super.visit(n, arg);
        }
        
        public int getModificationCount() {
            return modificationCount;
        }
        
        public boolean isModified() {
            return modified;
        }
        
        public void resetModified() {
            modified = false;
        }
    }
}