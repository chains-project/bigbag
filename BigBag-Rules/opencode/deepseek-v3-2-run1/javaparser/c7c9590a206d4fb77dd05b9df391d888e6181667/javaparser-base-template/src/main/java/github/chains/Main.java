package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation for Doxia Site Renderer 1.11.1 breaking change:
 * - Package moved: org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext 
 *   -> org.apache.maven.doxia.siterenderer.RenderingContext
 * - Constructor deprecated: RenderingContext(File, String) 
 *   -> RenderingContext(File, String, String) with generator parameter
 */
public class Main {
    
    // Configuration: Old and new fully-qualified type names
    private static final String OLD_PACKAGE = "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_PACKAGE = "org.apache.maven.doxia.siterenderer.RenderingContext";
    private static final String GENERATOR_VALUE = ""; // Default generator value
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Java files in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        int transformedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " file(s)");
    }
    
    private static boolean transformFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + javaFile)
            );
            
            boolean modified = false;
            
            // 1. Fix import statements
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.getNameAsString().equals(OLD_PACKAGE)) {
                    importDecl.setName(NEW_PACKAGE);
                    modified = true;
                    System.out.println("  Fixed import in " + javaFile);
                }
            }
            
            // 2. Fix constructor calls
            ConstructorFixVisitor visitor = new ConstructorFixVisitor();
            visitor.visit(cu, null);
            if (visitor.modified) {
                modified = true;
                System.out.println("  Fixed constructor calls in " + javaFile);
            }
            
            if (modified) {
                Files.write(javaFile, cu.toString().getBytes());
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private static class ConstructorFixVisitor extends VoidVisitorAdapter<Void> {
        boolean modified = false;
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a RenderingContext constructor call
            if (n.getType().asString().equals("RenderingContext")) {
                // Check if it's the deprecated 2-argument constructor
                if (n.getArguments().size() == 2) {
                    NodeList<Expression> args = n.getArguments();
                    
                    // Add generator parameter (empty string for compatibility)
                    args.add(new StringLiteralExpr(GENERATOR_VALUE));
                    modified = true;
                }
            }
        }
    }
}