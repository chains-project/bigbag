package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        HamcrestConstructorFixVisitor visitor = new HamcrestConstructorFixVisitor();
        
        int modifiedFiles = 0;
        int modifiedConstructors = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu != null) {
                    int modificationsBefore = visitor.getModificationCount();
                    cu.accept(visitor, null);
                    int modificationsAfter = visitor.getModificationCount();
                    
                    if (modificationsAfter > modificationsBefore) {
                        modifiedFiles++;
                        modifiedConstructors += (modificationsAfter - modificationsBefore);
                        
                        // Write back the modified file
                        Files.write(javaFile, cu.toString().getBytes());
                        System.out.println("Modified: " + javaFile);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("Modified files: " + modifiedFiles);
        System.out.println("Modified constructor calls: " + modifiedConstructors);
    }
    
    static class HamcrestConstructorFixVisitor extends ModifierVisitor<Void> {
        private int modificationCount = 0;
        
        public int getModificationCount() {
            return modificationCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            String typeName = n.getType().asString();
            
            // Check if this is a StringContains or StringStartsWith constructor call
            if ("StringContains".equals(typeName) || "StringStartsWith".equals(typeName)) {
                // Check if it has exactly 2 arguments (boolean, string) - the old pattern
                if (n.getArguments().size() == 2) {
                    // Check if the first argument is a boolean literal
                    // We'll remove the boolean parameter, keeping only the string
                    n.getArguments().remove(0);
                    modificationCount++;
                    System.out.println("  Fixed constructor: " + typeName + " at line " + n.getBegin().map(pos -> pos.line).orElse(-1));
                }
                // If it has 1 argument (string), it's already using the new API
                // If it has 0 or >2 arguments, we don't touch it
            }
            
            return super.visit(n, arg);
        }
    }
}