package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.QualifiedNameExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation rule for hamcrest library 2.2 breaking change.
 * Transforms imports and references from org.hamcrest.Matchers to
 * org.hamcrest.CoreMatchers.
 * 
 * This transformation handles:
 * 1. Simple imports: import org.hamcrest.Matchers; → import org.hamcrest.CoreMatchers;
 * 2. Fully-qualified references: org.hamcrest.Matchers.method() → org.hamcrest.CoreMatchers.method()
 * 3. Static imports: import static org.hamcrest.Matchers.*; → import static org.hamcrest.CoreMatchers.*;
 * 4. Method calls: Matchers.method() → CoreMatchers.method()
 */
public class Main {
    // Configuration: old and new fully-qualified class names
    private static final String OLD_CLASS = "org.hamcrest.Matchers";
    private static final String NEW_CLASS = "org.hamcrest.CoreMatchers";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms " + OLD_CLASS + " → " + NEW_CLASS);
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming " + OLD_CLASS + " to " + NEW_CLASS + "...");
        transformDirectory(sourceDir);
        System.out.println("Transformation complete.");
    }
    
    private static void transformDirectory(Path dir) throws IOException {
        Files.walk(dir)
            .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Warning: Could not parse " + file);
                return;
            }
            
            boolean modified = false;
            
            // Transform import declarations
            List<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                String importName = importDecl.getNameAsString();
                
                // Handle regular imports
                if (importName.equals(OLD_CLASS)) {
                    importDecl.setName(new Name(NEW_CLASS));
                    modified = true;
                    System.out.println("Updated import in " + file + ": " + OLD_CLASS + " → " + NEW_CLASS);
                }
                // Handle static imports
                else if (importDecl.isStatic() && importName.equals(OLD_CLASS)) {
                    importDecl.setName(new Name(NEW_CLASS));
                    modified = true;
                    System.out.println("Updated static import in " + file + ": " + OLD_CLASS + " → " + NEW_CLASS);
                }
                // Handle wildcard static imports
                else if (importDecl.isStatic() && importDecl.isAsterisk() && 
                         importName.equals(OLD_CLASS.substring(0, OLD_CLASS.lastIndexOf('.')))) {
                    // This handles: import static org.hamcrest.Matchers.*;
                    // The asterisk import would have package name only
                    importDecl.setName(new Name(NEW_CLASS.substring(0, NEW_CLASS.lastIndexOf('.'))));
                    modified = true;
                    System.out.println("Updated wildcard static import in " + file + 
                                     ": " + importName + ".* → " + NEW_CLASS.substring(0, NEW_CLASS.lastIndexOf('.')) + ".*");
                }
            }
            
            // Transform fully-qualified references in code
            cu.walk(Name.class, name -> {
                if (name.asString().equals(OLD_CLASS)) {
                    name.setQualifier(new Name(NEW_CLASS.substring(0, NEW_CLASS.lastIndexOf('.'))));
                    name.setIdentifier(NEW_CLASS.substring(NEW_CLASS.lastIndexOf('.') + 1));
                    System.out.println("Updated qualified reference in " + file + ": " + OLD_CLASS + " → " + NEW_CLASS);
                    modified = true;
                }
            });
            
            // Transform method calls (e.g., Matchers.method() -> CoreMatchers.method())
            cu.walk(NameExpr.class, nameExpr -> {
                if (nameExpr.getNameAsString().equals("Matchers")) {
                    // Check the context - is this a method call like Matchers.something()?
                    if (nameExpr.getParentNode().isPresent()) {
                        Node parent = nameExpr.getParentNode().get();
                        // Check if parent is a MethodCallExpr and Matchers is the scope
                        if (parent instanceof com.github.javaparser.ast.expr.MethodCallExpr) {
                            com.github.javaparser.ast.expr.MethodCallExpr methodCall = 
                                (com.github.javaparser.ast.expr.MethodCallExpr) parent;
                            if (methodCall.getScope().isPresent() && 
                                methodCall.getScope().get().equals(nameExpr)) {
                                // Change Matchers to CoreMatchers
                                nameExpr.setName("CoreMatchers");
                                System.out.println("Updated method call scope in " + file + ": Matchers → CoreMatchers");
                                modified = true;
                            }
                        }
                        // Also handle field access (though less common with Matchers)
                        else if (parent instanceof com.github.javaparser.ast.expr.FieldAccessExpr) {
                            com.github.javaparser.ast.expr.FieldAccessExpr fieldAccess = 
                                (com.github.javaparser.ast.expr.FieldAccessExpr) parent;
                            if (fieldAccess.getScope().equals(nameExpr)) {
                                nameExpr.setName("CoreMatchers");
                                System.out.println("Updated field access scope in " + file + ": Matchers → CoreMatchers");
                                modified = true;
                            }
                        }
                    }
                }
            });
            
            if (modified) {
                // Save the transformed file
                PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
                config.setPrintComments(true);
                config.setOrderImports(true);
                PrettyPrinter printer = new PrettyPrinter(config);
                String transformed = printer.print(cu);
                Files.write(file, transformed.getBytes());
            }
            
        } catch (IOException e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
}
