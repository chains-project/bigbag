package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic JavaParser transformation to fix breaking API changes.
 * This transformation handles constructor API changes where a boolean parameter
 * needs to be removed from the beginning of the parameter list.
 * 
 * The transformation is configurable for different classes and can handle
 * multiple target classes in one run.
 * 
 * Usage: java github.chains.Main <source-directory> <class1> <class2> ...
 * Example: java github.chains.Main /path/to/project org.hamcrest.core.StringContains org.hamcrest.core.StringStartsWith
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-directory> <fully-qualified-class-name>...");
            System.err.println("Example: java github.chains.Main /path/to/project org.hamcrest.core.StringContains org.hamcrest.core.StringStartsWith");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        List<String> targetClasses = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            targetClasses.add(args[i]);
        }
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Target classes: " + targetClasses);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int totalChanges = 0;
            for (Path javaFile : javaFiles) {
                int changes = processFile(javaFile, targetClasses);
                if (changes > 0) {
                    System.out.println("Modified " + javaFile + " (" + changes + " changes)");
                    totalChanges += changes;
                }
            }
            
            System.out.println("Total changes made: " + totalChanges);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static int processFile(Path javaFile, List<String> targetClasses) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + javaFile)
        );
        
        ConstructorFixVisitor visitor = new ConstructorFixVisitor(targetClasses);
        cu.accept(visitor, null);
        
        if (visitor.getChangeCount() > 0) {
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(cu.toString());
            }
        }
        
        return visitor.getChangeCount();
    }
    
    /**
     * Visitor that fixes constructor calls by removing the first boolean parameter
     * when a constructor has exactly 2 parameters (boolean, String) for target classes.
     */
    private static class ConstructorFixVisitor extends VoidVisitorAdapter<Void> {
        private final List<String> targetClasses;
        private int changeCount = 0;
        
        public ConstructorFixVisitor(List<String> targetClasses) {
            this.targetClasses = targetClasses;
        }
        
        public int getChangeCount() {
            return changeCount;
        }
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            
            String typeName = expr.getType().asString();
            
            // Check if this is a constructor call for any of our target classes
            for (String targetClass : targetClasses) {
                String simpleName = getSimpleClassName(targetClass);
                
                if (typeName.equals(simpleName) || typeName.equals(targetClass)) {
                    // Check if it has exactly 2 arguments
                    if (expr.getArguments().size() == 2) {
                        // Check if first argument is a boolean literal (true/false)
                        // and second argument is a String literal or expression
                        boolean firstIsBoolean = expr.getArgument(0).isBooleanLiteralExpr() ||
                                               expr.getArgument(0).isNameExpr() || // Could be a boolean variable
                                               expr.getArgument(0).toString().equals("true") ||
                                               expr.getArgument(0).toString().equals("false");
                        
                        boolean secondIsString = expr.getArgument(1).isStringLiteralExpr() ||
                                                expr.getArgument(1).isNameExpr() || // Could be a String variable
                                                expr.getArgument(1).isMethodCallExpr(); // Could be a method returning String
                        
                        if (firstIsBoolean && secondIsString) {
                            // Remove the first (boolean) argument
                            expr.getArguments().remove(0);
                            changeCount++;
                            System.out.println("  Fixed " + targetClass + " constructor at line " + 
                                expr.getRange().map(r -> r.begin.line).orElse(-1));
                            break; // Found and fixed, move to next expression
                        }
                    }
                }
            }
        }
        
        private String getSimpleClassName(String fullClassName) {
            int lastDot = fullClassName.lastIndexOf('.');
            return lastDot == -1 ? fullClassName : fullClassName.substring(lastDot + 1);
        }
    }
}