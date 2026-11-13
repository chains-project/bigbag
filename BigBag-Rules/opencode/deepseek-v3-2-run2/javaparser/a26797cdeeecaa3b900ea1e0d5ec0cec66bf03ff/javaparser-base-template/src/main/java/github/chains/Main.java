package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for logback-classic 1.4.8 breaking change.
 * 
 * Breaking Change: ch.qos.logback.classic.Logger now implements 
 * org.slf4j.spi.LoggingEventAware (from slf4j-api 2.0.x), causing compilation
 * errors with slf4j-api 1.7.x.
 * 
 * Transformation: Changes code pattern
 *   (Logger) LoggerFactory.getLogger(...)
 * to
 *   ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(...)
 * 
 * This avoids casting the result of LoggerFactory.getLogger() to Logger,
 * which requires checking that Logger implements LoggingEventAware.
 * Instead, we get a Logger from LoggerContext directly.
 * 
 * Note: Projects also need to update slf4j-api to 2.0.x to fully resolve
 * compilation errors.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            // Setup JavaParser with symbol solver
            CombinedTypeSolver typeSolver = new CombinedTypeSolver();
            typeSolver.add(new ReflectionTypeSolver());
            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            JavaParser javaParser = new JavaParser();
            javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
            
            // Find all Java files
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int filesProcessed = 0;
            int transformationsApplied = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) {
                        continue;
                    }
                    
                    boolean modified = false;
                    
                    // Check for imports of ch.qos.logback.classic.Logger
                    boolean hasLoggerImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals("ch.qos.logback.classic.Logger"));
                    
                    if (hasLoggerImport) {
                        // Find all cast expressions
                        List<CastExpr> castExprs = cu.findAll(CastExpr.class);
                        for (CastExpr castExpr : castExprs) {
                            // Check if this is casting to Logger (simple string match)
                            String castTypeStr = castExpr.getType().toString();
                            if (castTypeStr.equals("Logger") || castTypeStr.equals("ch.qos.logback.classic.Logger")) {
                                
                                // Check if this is casting from LoggerFactory.getLogger()
                                if (castExpr.getExpression() instanceof MethodCallExpr) {
                                    MethodCallExpr methodCall = (MethodCallExpr) castExpr.getExpression();
                                    if (methodCall.getNameAsString().equals("getLogger") &&
                                        methodCall.getScope().isPresent() &&
                                        methodCall.getScope().get().toString().equals("LoggerFactory")) {
                                        
                                        // Transform: (Logger) LoggerFactory.getLogger(X) 
                                        // to: ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(X)
                                        
                                        // Get the argument from getLogger()
                                        NodeList<com.github.javaparser.ast.expr.Expression> methodArgs = methodCall.getArguments();
                                        
                                        // Create new expression: ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(...)
                                        MethodCallExpr getILoggerFactory = new MethodCallExpr(new NameExpr("LoggerFactory"), "getILoggerFactory");
                                        CastExpr castToLoggerContext = new CastExpr(new ClassOrInterfaceType("ch.qos.logback.classic.LoggerContext"), getILoggerFactory);
                                        EnclosedExpr enclosedCast = new EnclosedExpr(castToLoggerContext);
                                        MethodCallExpr newGetLogger = new MethodCallExpr(enclosedCast, "getLogger", methodArgs);
                                        
                                        // Replace the cast expression with the new expression
                                        castExpr.replace(newGetLogger);
                                        modified = true;
                                        transformationsApplied++;
                                    }
                                }
                            }
                        }
                        
                        // Add import for LoggerContext if not already present
                        boolean hasLoggerContextImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("ch.qos.logback.classic.LoggerContext"));
                        
                        if (!hasLoggerContextImport && modified) {
                            cu.addImport("ch.qos.logback.classic.LoggerContext");
                        }
                    }
                    
                    if (modified) {
                        // Write the modified file back
                        Files.write(javaFile, cu.toString().getBytes());
                        System.out.println("Modified: " + javaFile);
                    }
                    
                    filesProcessed++;
                    
                } catch (Exception e) {
                    System.err.println("Error processing file: " + javaFile);
                    e.printStackTrace();
                }
            }
            
            System.out.println("Processing complete.");
            System.out.println("Files processed: " + filesProcessed);
            System.out.println("Transformations applied: " + transformationsApplied);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}