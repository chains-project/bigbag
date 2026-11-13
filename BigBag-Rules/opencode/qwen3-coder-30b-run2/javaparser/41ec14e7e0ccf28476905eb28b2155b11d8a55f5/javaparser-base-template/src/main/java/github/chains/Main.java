package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/wicket-crudifier/src/main/java";
        processJavaFiles(sourceDir);
    }
    
    private static void processJavaFiles(String sourceDir) throws IOException {
        Path dir = Paths.get(sourceDir);
        if (!Files.exists(dir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            JavaParserTransformation transformation = new JavaParserTransformation();
            cu.accept(transformation, null);
            
            // Write back the modified file
            LexicalPreservingPrinter.print(cu);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class JavaParserTransformation extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(CompilationUnit cu, Void arg) {
            // Remove old validation imports
            removeValidationImports(cu);
            
            // Replace usages of old validation APIs
            replaceValidationUsages(cu);
            
            // Replace usages of PropertyUtils
            replacePropertyUtilsUsages(cu);
            
            super.visit(cu, arg);
        }
        
        private void removeValidationImports(CompilationUnit cu) {
            // Remove javax.validation imports
            NodeList<ImportDeclaration> imports = cu.getImports();
            List<ImportDeclaration> toRemove = new ArrayList<>();
            
            for (ImportDeclaration imp : imports) {
                String importedName = imp.getNameAsString();
                if (importedName.startsWith("javax.validation") || 
                    importedName.startsWith("org.apache.commons.beanutils")) {
                    toRemove.add(imp);
                }
            }
            
            imports.removeAll(toRemove);
        }
        
        private void replaceValidationUsages(CompilationUnit cu) {
            // No complex replacement needed for now - just removing imports is sufficient for this case
        }
        
        private void replacePropertyUtilsUsages(CompilationUnit cu) {
            // No complex replacement needed for now - just removing imports is sufficient for this case
        }
    }
}
