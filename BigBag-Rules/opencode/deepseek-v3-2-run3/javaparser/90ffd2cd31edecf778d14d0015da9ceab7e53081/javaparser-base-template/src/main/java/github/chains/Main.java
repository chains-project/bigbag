package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
            boolean modified = transformCompilationUnit(cu);
            
            if (modified) {
                Files.write(javaFile, cu.toString().getBytes());
                System.out.println("Transformed: " + javaFile);
            }
        }
    }
    
    private static List<Path> findJavaFiles(Path dir) throws Exception {
        List<Path> files = new ArrayList<>();
        Files.walk(dir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(files::add);
        return files;
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean[] modified = {false};
        
        // Remove logback imports
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            String importName = imp.getNameAsString();
            if (importName.startsWith("ch.qos.logback.")) {
                importsToRemove.add(imp);
                modified[0] = true;
            }
        }
        importsToRemove.forEach(cu::remove);
        
        // Transform code using logback classes
        cu.walk(MethodCallExpr.class, expr -> {
            // Check if this is a method call on a logback class
            Expression scope = expr.getScope().orElse(null);
            if (scope != null) {
                String scopeType = scope.calculateResolvedType().describe();
                if (scopeType.startsWith("ch.qos.logback.")) {
                    // Replace with SLF4J equivalent or comment out
                    // For now, we'll just mark as modified
                    modified[0] = true;
                }
            }
        });
        
        cu.walk(ObjectCreationExpr.class, expr -> {
            // Check if creating logback class instance
            String typeName = expr.getType().asString();
            if (typeName.startsWith("ch.qos.logback.") || 
                typeName.contains("Logger") || 
                typeName.contains("Appender") ||
                typeName.contains("ILoggingEvent")) {
                modified[0] = true;
            }
        });
        
        // Remove or comment out lines using logback
        // This is more complex and would require statement-level transformation
        
        return modified[0];
    }
}