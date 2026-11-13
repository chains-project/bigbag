package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-dir> <old-class-fqdn> <new-class-fqdn>");
            System.err.println("Example: java github.chains.Main /path/to/src org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldClassFqdn = args[1];
        String newClassFqdn = args.length > 2 ? args[2] : null;
        
        try {
            List<Path> javaFiles = new ArrayList<>();
            Files.walk(Paths.get(sourceDir))
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(javaFiles::add);
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            JavaParser javaParser = new JavaParser();
            int modifiedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow();
                    boolean modified = false;
                    
                    // 1. Replace imports
                    for (ImportDeclaration importDecl : cu.getImports()) {
                        if (importDecl.getNameAsString().equals(oldClassFqdn)) {
                            if (newClassFqdn != null) {
                                importDecl.setName(newClassFqdn);
                                modified = true;
                                System.out.println("Replaced import: " + oldClassFqdn + " -> " + newClassFqdn + " in " + javaFile);
                            } else {
                                // Remove import if no replacement
                                cu.remove(importDecl);
                                modified = true;
                                System.out.println("Removed import: " + oldClassFqdn + " from " + javaFile);
                            }
                        }
                    }
                    
                    // 2. Replace object creations with null (simplest compilation fix)
                    cu.accept(new ModifierVisitor<Void>() {
                        @Override
                        public Visitable visit(ObjectCreationExpr n, Void arg) {
                            // Check if this is creating the old class
                            if (n.getType().toString().endsWith(getSimpleName(oldClassFqdn))) {
                                System.out.println("Found object creation of " + oldClassFqdn + " in " + javaFile);
                                
                                // Replace with null and add comment
                                // Parse "null" expression
                                try {
                                    com.github.javaparser.ast.expr.NullLiteralExpr nullExpr = 
                                        new com.github.javaparser.ast.expr.NullLiteralExpr();
                                    
                                    // Add comment about the replacement
                                    nullExpr.setComment(new com.github.javaparser.ast.comments.LineComment(
                                        " TODO: " + getSimpleName(oldClassFqdn) + " was removed. " +
                                        (newClassFqdn != null ? "Use " + getSimpleName(newClassFqdn) + " instead." : 
                                         "Class was removed in API update.")));
                                    
                                    return nullExpr;
                                } catch (Exception e) {
                                    System.err.println("Could not create null replacement for " + n + " in " + javaFile);
                                    return n;
                                }
                            }
                            return super.visit(n, arg);
                        }
                        
                        private String getSimpleName(String fqdn) {
                            int lastDot = fqdn.lastIndexOf('.');
                            return lastDot >= 0 ? fqdn.substring(lastDot + 1) : fqdn;
                        }
                    }, null);
                    
                    if (modified) {
                        // Write back changes
                        Files.write(javaFile, cu.toString().getBytes());
                        modifiedFiles++;
                    }
                    
                } catch (FileNotFoundException e) {
                    System.err.println("File not found: " + javaFile);
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("Modified " + modifiedFiles + " files");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}