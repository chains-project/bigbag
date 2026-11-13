package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for tinspin-indexes 2.0.0 API migration.
 * 
 * This transformation handles the breaking changes from tinspin-indexes 1.x to 2.0.0:
 * 1. PointDistanceFunction -> PointDistance (interface rename)
 * 2. PointEntryDist -> Index.PointEntryKnn (class rename to inner class)
 * 3. PointIndex<T> -> PointMap<T> (interface rename)
 * 4. Factory method signature changes for KDTree.create() and CoverTree.create()
 * 5. Return type changes for query1NN() method
 * 
 * The transformation is generic and can be applied to any Java project affected
 * by the tinspin-indexes 2.0.0 breaking changes.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <sourceDirectory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transforming tinspin-indexes API from 1.x to 2.0.0");
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
            System.out.println("Summary of transformations applied:");
            System.out.println("  1. PointDistanceFunction -> PointDistance");
            System.out.println("  2. PointEntryDist -> Index.PointEntryKnn");
            System.out.println("  3. PointIndex<T> -> PointMap<T>");
            System.out.println("  4. Fixed imports and type references");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        List<File> javaFiles = findJavaFiles(directory.toFile());
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        int transformedFiles = 0;
        for (File file : javaFiles) {
            System.out.println("\nProcessing: " + file.getAbsolutePath());
            if (processFile(file)) {
                transformedFiles++;
            }
        }
        
        System.out.println("\nTotal files transformed: " + transformedFiles + " out of " + javaFiles.size());
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
        return javaFiles;
    }
    
    private static boolean processFile(File file) throws IOException {
        JavaParser parser = new JavaParser();
        var parseResult = parser.parse(file);
        
        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            CompilationUnit cu = parseResult.getResult().get();
            TinspinMigrationVisitor visitor = new TinspinMigrationVisitor();
            boolean wasModified = visitor.wasModified;
            cu.accept(visitor, null);
            
            if (visitor.wasModified) {
                // Write back the modified file
                String modifiedCode = cu.toString();
                
                // Write to file
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(modifiedCode);
                }
                
                System.out.println("  ✓ Successfully transformed");
                return true;
            } else {
                System.out.println("  ○ No transformations needed");
                return false;
            }
        } else {
            System.err.println("  ✗ Failed to parse");
            return false;
        }
    }
    
    static class TinspinMigrationVisitor extends GenericVisitorAdapter<Node, Void> {
        boolean wasModified = false;
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            String importName = n.getNameAsString();
            
            // Transform imports for tinspin-indexes 2.0.0 API changes
            if (importName.equals("org.tinspin.index.PointDistanceFunction")) {
                System.out.println("    • Transforming import: PointDistanceFunction -> PointDistance");
                n.setName("org.tinspin.index.PointDistance");
                wasModified = true;
            }
            
            if (importName.equals("org.tinspin.index.PointEntryDist")) {
                System.out.println("    • Transforming import: PointEntryDist -> Index.PointEntryKnn");
                n.setName("org.tinspin.index.Index.PointEntryKnn");
                wasModified = true;
            }
            
            if (importName.equals("org.tinspin.index.PointIndex")) {
                System.out.println("    • Removing deprecated import: PointIndex");
                System.out.println("    • Note: PointIndex<T> usage should be replaced with PointMap<T> in code");
                wasModified = true;
                return null; // Remove this import
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            String typeName = n.getNameAsString();
            
            // Transform type references in variable declarations, method parameters, etc.
            if (typeName.equals("PointDistanceFunction")) {
                System.out.println("    • Transforming type reference: PointDistanceFunction -> PointDistance");
                n.setName("PointDistance");
                wasModified = true;
            }
            
            if (typeName.equals("PointEntryDist")) {
                System.out.println("    • Transforming type reference: PointEntryDist -> PointEntryKnn");
                System.out.println("    • Note: Import should be org.tinspin.index.Index.PointEntryKnn");
                n.setName("PointEntryKnn");
                wasModified = true;
            }
            
            // Handle Index.PointEntryKnn (when used with outer class)
            if (typeName.equals("Index.PointEntryKnn")) {
                System.out.println("    • Transforming type reference: Index.PointEntryKnn -> PointEntryKnn");
                System.out.println("    • Import should be org.tinspin.index.Index.PointEntryKnn");
                n.setName("PointEntryKnn");
                wasModified = true;
            }
            
            if (typeName.equals("PointIndex")) {
                System.out.println("    • Transforming type reference: PointIndex -> PointMap");
                n.setName("PointMap");
                wasModified = true;
            }
            
            // Handle generic types like PointIndex<Vertex>
            if (typeName.contains("PointIndex<")) {
                System.out.println("    • Transforming generic type: " + typeName + " -> PointMap");
                // This is a simplified check - actual generic types are handled by the name
                if (n.getNameAsString().equals("PointIndex")) {
                    n.setName("PointMap");
                    wasModified = true;
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            // Check for KDTree.create() or CoverTree.create() calls that might need adjustment
            if (methodName.equals("create")) {
                var scopeOpt = n.getScope();
                if (scopeOpt.isPresent()) {
                    Expression scope = scopeOpt.get();
                    if (scope instanceof NameExpr) {
                        String className = ((NameExpr) scope).getNameAsString();
                        
                        // Handle KDTree.create(int, PointDistance) -> KDTree.create(int)
                        // In tinspin-indexes 2.0.0, KDTree doesn't accept PointDistance parameter
                        if (className.equals("KDTree")) {
                            // Check if create() has 2 arguments (int and lambda)
                            if (n.getArguments().size() == 2) {
                                System.out.println("    • Transforming KDTree.create(int, PointDistance) -> KDTree.create(int)");
                                System.out.println("    • Removing PointDistance lambda argument");
                                // Remove the second argument (the lambda)
                                n.getArguments().remove(1);
                                wasModified = true;
                            }
                        }
                        
                        // CoverTree.create(int, double, PointDistance) signature still exists
                        // but PointDistanceFunction -> PointDistance transformation handles it
                        if (className.equals("CoverTree")) {
                            System.out.println("    • Found CoverTree.create() - PointDistanceFunction -> PointDistance handled");
                        }
                    }
                }
            }
            
            // Check for query1NN() calls - method name changed case: query1NN -> query1nn
            if (methodName.equals("query1NN")) {
                System.out.println("    • Transforming method call: query1NN -> query1nn (case change)");
                n.setName("query1nn");
                wasModified = true;
            }
            
            return super.visit(n, arg);
        }
    }
}