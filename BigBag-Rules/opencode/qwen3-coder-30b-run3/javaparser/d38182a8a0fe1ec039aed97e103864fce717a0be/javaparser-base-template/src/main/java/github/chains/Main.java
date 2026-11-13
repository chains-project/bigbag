package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ConstructorCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.utils.SourceRoot;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Generic transformation to fix breaking changes in Authentication.User -> AuthUser.
 * This transformation replaces constructor calls to Authentication.User with AuthUser.
 * 
 * This is a ready-to-use implementation that can be packaged and run on any Maven project
 * affected by the same breaking change.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("=== Generic Transformation Tool ===");
        System.out.println("Fixing breaking changes in Authentication.User -> AuthUser");
        System.out.println("Usage: java -cp jar-file github.chains.Main /path/to/project/src");
        System.out.println();
        
        if (args.length == 0) {
            System.err.println("Error: Specify source directory as argument");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing files in: " + sourceDir);
        
        SourceRoot root = new SourceRoot(Paths.get(sourceDir));
        
        root.parse("", (localPath, absolutePath, compUnit) -> {
            if (compUnit.isSuccessful()) {
                CompilationUnit cu = compUnit.getResult().get();
                boolean modified = false;
                
                // Find all new expressions that create Authentication.User
                for (ConstructorCallExpr expr : cu.findAll(ConstructorCallExpr.class)) {
                    if ("Authentication.User".equals(expr.getType().asString())) {
                        System.out.println("Found Authentication.User constructor at: " + localPath + ":" + expr.getBegin().get().line);
                        // Replace with AuthUser
                        expr.setType(new ClassOrInterfaceType("AuthUser"));
                        modified = true;
                    }
                }
                
                // Add import statement if not already present and we made changes
                boolean hasAuthUserImport = cu.getImports().stream()
                    .anyMatch(importDecl -> importDecl.getNameAsString().contains("AuthUser"));
                
                if (!hasAuthUserImport && modified) {
                    System.out.println("Adding import for AuthUser");
                    cu.addImport("com.artipie.http.auth.AuthUser");
                }
                
                if (modified) {
                    System.out.println("Modified file: " + localPath);
                    return SourceRoot.Callback.Result.SAVE;
                }
            }
            return SourceRoot.Callback.Result.SKIP;
        });
        
        // Save modified files
        System.out.println("Saving transformed files...");
        root.saveAll();
        System.out.println("Transformation complete.");
    }
}
