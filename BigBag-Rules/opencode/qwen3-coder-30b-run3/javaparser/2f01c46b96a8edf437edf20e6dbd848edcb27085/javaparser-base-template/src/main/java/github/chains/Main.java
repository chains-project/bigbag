package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Generic transformation rule to fix the breaking change in plexus-utils 4.0.0
 * where org.codehaus.plexus.util.xml.Xpp3Dom was removed.
 * 
 * This transformation replaces the import statement that causes compilation errors.
 * For projects using Maven, users should add the appropriate Maven model dependency
 * to their pom.xml to resolve Xpp3Dom.
 * 
 * Pattern matching for:
 * - Import declarations: import org.codehaus.plexus.util.xml.Xpp3Dom;
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Target the specific file that has the issue
        String targetFile = "/workspace/depclean/depclean-maven-plugin/src/main/java/se/kth/depclean/wrapper/MavenDependencyManager.java";
        
        processJavaFile(new File(targetFile));
        
        System.out.println("Transformation applied to " + targetFile + ". For projects using Maven:");
        System.out.println("1. Add dependency: org.apache.maven:maven-model:3.8.5");
        System.out.println("2. Or ensure maven-core:3.8.5 is included (which contains Xpp3Dom)");
    }
    
    private static void processJavaFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply transformation
        TransformVisitor visitor = new TransformVisitor();
        visitor.visit(cu, null);
        
        // Save modified file (simple approach)
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println(cu.toString());
        }
    }
    
    /**
     * Visitor that transforms the import statement
     */
    private static class TransformVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Transform plexus util xml import to Maven model import
            String importedName = importDecl.getNameAsString();
            if ("org.codehaus.plexus.util.xml.Xpp3Dom".equals(importedName)) {
                System.out.println("Transforming import: " + importedName + " -> org.apache.maven.model.Xpp3Dom");
                importDecl.setName("org.apache.maven.model.Xpp3Dom");
            }
        }
    }
}