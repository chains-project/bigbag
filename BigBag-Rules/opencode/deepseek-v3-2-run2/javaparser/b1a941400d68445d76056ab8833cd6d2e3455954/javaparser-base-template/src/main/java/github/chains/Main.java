package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    // Configuration for the transformation
    private static final String TARGET_CLASS = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String SIMPLE_TARGET_CLASS = "Constructor";
    private static final String ARGUMENT_CLASS = "org.yaml.snakeyaml.LoaderOptions";
    private static final String SIMPLE_ARGUMENT_CLASS = "LoaderOptions";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("\nThis transformation fixes snakeyaml 2.0 breaking change:");
            System.err.println("  Old: new Constructor()");
            System.err.println("  New: new Constructor(new LoaderOptions())");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transformation: " + TARGET_CLASS + "() -> " + TARGET_CLASS + "(new " + SIMPLE_ARGUMENT_CLASS + "())");
        System.out.println("Scanning directory: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalChanges = 0;
        int totalFilesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElseThrow();
                boolean hasTargetImport = hasImportForClass(cu, TARGET_CLASS);
                String simpleNameInFile = getSimpleNameForImport(cu, TARGET_CLASS);
                
                ConstructorTransformer visitor = new ConstructorTransformer(hasTargetImport, simpleNameInFile);
                cu.accept(visitor, null);
                
                if (visitor.getChangeCount() > 0) {
                    // Add import for ARGUMENT_CLASS if we made changes and it's not already imported
                    if (!hasImportForClass(cu, ARGUMENT_CLASS)) {
                        cu.addImport(ARGUMENT_CLASS);
                    }
                    
                    Files.write(javaFile, cu.toString().getBytes());
                    totalChanges += visitor.getChangeCount();
                    totalFilesModified++;
                    System.out.println("Modified: " + javaFile + " (" + visitor.getChangeCount() + " changes)");
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nMigration complete:");
        System.out.println("  Total files modified: " + totalFilesModified);
        System.out.println("  Total changes made: " + totalChanges);
    }
    
    private static boolean hasImportForClass(CompilationUnit cu, String className) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(className) || 
                imp.getNameAsString().equals(className + ".*")) {
                return true;
            }
        }
        return false;
    }
    
    private static String getSimpleNameForImport(CompilationUnit cu, String className) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        
        // Check if the class is imported with its simple name
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(className)) {
                return simpleName;
            }
        }
        
        // Check if it's imported via wildcard
        String packageName = className.substring(0, className.lastIndexOf('.'));
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(packageName + ".*")) {
                return simpleName;
            }
        }
        
        // Not imported, so we need to use fully qualified name
        return className;
    }
    
    static class ConstructorTransformer extends ModifierVisitor<Void> {
        private final boolean hasTargetImport;
        private final String targetSimpleName;
        private int changeCount = 0;
        
        public ConstructorTransformer(boolean hasTargetImport, String targetSimpleName) {
            this.hasTargetImport = hasTargetImport;
            this.targetSimpleName = targetSimpleName;
        }
        
        public int getChangeCount() {
            return changeCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            String typeName = n.getType().asString();
            
            // We need to check if this is our target class
            // Cases:
            // 1. Simple name matches and we have the import: "Constructor"
            // 2. Fully qualified name matches: "org.yaml.snakeyaml.constructor.Constructor"
            // 3. Simple name matches but we don't have import - could be a different Constructor class
            //    We should be conservative and not modify unless we're sure
            
            boolean isTargetClass = false;
            
            if (typeName.equals(SIMPLE_TARGET_CLASS)) {
                if (hasTargetImport) {
                    // We have the import, so this is likely our target
                    isTargetClass = true;
                } else if (typeName.equals(targetSimpleName) && targetSimpleName.contains(".")) {
                    // targetSimpleName is fully qualified (not imported), and typeName matches simple part
                    // This means the class is not imported, but we're using simple name
                    // We should check if this could be our class
                    isTargetClass = true;
                }
                // Otherwise, it might be a different Constructor class - don't modify
            } else if (typeName.equals(TARGET_CLASS)) {
                // Fully qualified name
                isTargetClass = true;
            }
            
            if (isTargetClass && n.getArguments().isEmpty()) {
                // Transform: new Constructor() -> new Constructor(new LoaderOptions())
                ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                loaderOptions.setType(SIMPLE_ARGUMENT_CLASS);
                
                n.getArguments().add(loaderOptions);
                changeCount++;
                System.out.println("  Fixed: new " + typeName + "() -> new " + typeName + "(new " + SIMPLE_ARGUMENT_CLASS + "())");
            }
            
            return super.visit(n, arg);
        }
    }
}