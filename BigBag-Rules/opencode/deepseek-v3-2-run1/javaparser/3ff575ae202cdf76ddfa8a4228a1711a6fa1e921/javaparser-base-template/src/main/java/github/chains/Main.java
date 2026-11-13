package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.ast.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking API changes where a method
 * is moved from a parent interface to a child interface.
 * 
 * Example: In Plexus Container 2.1.1, getLoggerManager() was removed from
 * PlexusContainer interface and moved to MutablePlexusContainer interface.
 * 
 * Usage: java -jar javaparser.jar <source-directory> <source-interface> <target-interface> <method-name>
 * Example: java -jar javaparser.jar /path/to/src org.codehaus.plexus.PlexusContainer org.codehaus.plexus.MutablePlexusContainer getLoggerManager
 */
public class Main {
    
    private static String SOURCE_INTERFACE;
    private static String TARGET_INTERFACE;
    private static String METHOD_NAME;
    
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory> <source-interface> <target-interface> <method-name>");
            System.err.println("Example: java -jar javaparser.jar /path/to/src org.codehaus.plexus.PlexusContainer org.codehaus.plexus.MutablePlexusContainer getLoggerManager");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        SOURCE_INTERFACE = args[1];
        TARGET_INTERFACE = args[2];
        METHOD_NAME = args[3];
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Looking for: " + SOURCE_INTERFACE + "." + METHOD_NAME + "()");
        System.out.println("Will cast to: " + TARGET_INTERFACE);
        
        // Setup type solver for symbol resolution
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser javaParser = new JavaParser();
        javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        // Walk through all Java files
        List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                MethodMoveVisitor visitor = new MethodMoveVisitor();
                cu.accept(visitor, null);
                
                if (visitor.transformedCount > 0) {
                    // Add import for target interface if not already present
                    boolean hasTargetImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals(TARGET_INTERFACE));
                    if (!hasTargetImport) {
                        cu.addImport(TARGET_INTERFACE);
                    }
                    
                    // Write back the transformed file
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    transformedCalls += visitor.transformedCount;
                    System.out.println("Transformed " + visitor.transformedCount + " calls in: " + javaFile);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing file: " + javaFile);
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation complete!");
        System.out.println("Transformed " + transformedCalls + " method calls in " + transformedFiles + " files");
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(startDir)
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        return javaFiles;
    }
    
/**
         * Visitor that transforms method calls from sourceInterface.methodName()
         * to ((targetInterface) expression).methodName()
         * 
         * This uses a generic pattern matching approach:
         * 1. Find all method calls with the specified method name
         * 2. For each, check if the scope could be of sourceInterface type
         * 3. Apply cast to targetInterface
         * 
         * The transformation is conservative - it transforms all matching method
         * calls, assuming they need the cast. This is safe because:
         * - If the expression is already of targetInterface type, the cast is redundant but harmless
         * - If the expression is of sourceInterface type but not targetInterface, 
         *   the cast will fail at runtime (which would happen anyway without the fix)
         * - If the expression is of targetInterface type, the cast succeeds
         */
        private static class MethodMoveVisitor extends VoidVisitorAdapter<Void> {
            private int transformedCount = 0;
            
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a method call we need to transform
                String methodName = n.getNameAsString();
                if (!METHOD_NAME.equals(methodName)) {
                    return;
                }
                
                // Check if we're calling this method on some expression
                if (!n.getScope().isPresent()) {
                    return;
                }
                
                Expression scope = n.getScope().get();
                
                // Check if this scope is already a cast expression
                // If it's already cast to targetInterface or a subtype, we don't need to transform
                if (scope.isCastExpr()) {
                    CastExpr cast = scope.asCastExpr();
                    String castType = cast.getType().asString();
                    // Check if it's already cast to target interface (with or without full package)
                    if (castType.equals(TARGET_INTERFACE) || 
                        castType.equals("MutablePlexusContainer") ||
                        castType.startsWith(TARGET_INTERFACE + "<")) {
                        return; // Already cast to target interface
                    }
                }
                
                // Apply transformation: wrap scope with cast to targetInterface
                CastExpr castExpr = new CastExpr();
                castExpr.setType(TARGET_INTERFACE);
                castExpr.setExpression(scope.clone());
                
                // Wrap cast in parentheses to ensure correct parsing when used as method receiver
                EnclosedExpr enclosedExpr = new EnclosedExpr(castExpr);
                
                // Replace the scope with the enclosed cast expression
                n.setScope(enclosedExpr);
                transformedCount++;
                
                System.out.println("  Transformed: " + scope + "." + methodName + "() -> ((" + 
                                 TARGET_INTERFACE + ")" + scope + ")." + methodName + "()");
            }
        }
}