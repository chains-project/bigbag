package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    /**
     * Generic transformation rule for Mockito 4.x to 5.x migration:
     * Converts invocation.getArgumentAt(index, type) to invocation.getArgument(index, type)
     * 
     * This is a breaking change in Mockito 5.x where getArgumentAt was renamed to getArgument.
     * The rule works for any project using Mockito, not specific to bval.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            int transformedFiles = transformProject(sourceDir);
            System.out.println("Transformation complete. Modified " + transformedFiles + " file(s).");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Transforms all Java files in the given directory recursively.
     * Returns the number of files modified.
     */
    public static int transformProject(String sourceDir) throws IOException {
        // Initialize type solver for method resolution
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        // Collect all Java files
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java file(s)");
        
        int modifiedCount = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
                MockitoMigrationVisitor visitor = new MockitoMigrationVisitor();
                visitor.visit(cu, null);
                
                if (visitor.isModified()) {
                    // Write back the modified file
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("Modified: " + javaFile);
                    modifiedCount++;
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not process " + javaFile + ": " + e.getMessage());
            }
        }
        
        return modifiedCount;
    }
    
    /**
     * Finds all Java files recursively in the given directory.
     */
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Visitor that identifies and transforms Mockito 4.x getArgumentAt calls to 5.x getArgument.
     * This transformation is generic and works for any project using Mockito.
     */
    private static class MockitoMigrationVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a getArgumentAt method call
            if (n.getName().getIdentifier().equals("getArgumentAt")) {
                // We could add type resolution here to check if the method is called
                // on an InvocationOnMock type, but for simplicity and to handle
                // all cases (including mock() calls), we transform all getArgumentAt calls.
                
                // Transform getArgumentAt to getArgument
                n.setName("getArgument");
                modified = true;
                System.out.println("Transformed getArgumentAt -> getArgument at line " + 
                    n.getBegin().map(pos -> pos.line).orElse(-1));
            }
        }
    }
}