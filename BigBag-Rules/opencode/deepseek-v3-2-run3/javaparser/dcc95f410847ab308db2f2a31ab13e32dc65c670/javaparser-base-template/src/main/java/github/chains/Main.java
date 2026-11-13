package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic transformation rule for fixing logback-classic 1.4.5 compatibility issues
 * when using SLF4J API < 2.0.0.
 * 
 * Breaking Change: ch.qos.logback.classic.Logger implements org.slf4j.spi.LoggingEventAware
 * but this interface doesn't exist in SLF4J API < 2.0.0.
 * 
 * Transformation pattern:
 * OLD: Logger logger = (Logger) LoggerFactory.getLogger(X.class);
 *      logger.setLevel(Level.INFO);
 *      logger.addAppender(appender);
 * 
 * NEW: Object logger = LoggerFactory.getLogger(X.class);
 *      try {
 *          if (logger.getClass().getName().equals("ch.qos.logback.classic.Logger")) {
 *              Method setLevelMethod = logger.getClass().getMethod("setLevel", Level.class);
 *              setLevelMethod.invoke(logger, Level.INFO);
 *              Method addAppenderMethod = logger.getClass().getMethod("addAppender", Appender.class);
 *              addAppenderMethod.invoke(logger, appender);
 *          }
 *      } catch (Exception e) {
 *          // Ignore - logger might not be logback
 *      }
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying logback compatibility transformation to: " + sourceDir);
        
        try {
            List<Path> transformedFiles = new ArrayList<>();
            
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(filePath -> {
                    try {
                        JavaParser parser = new JavaParser();
                        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
                        
                        boolean modified = false;
                        
                        // Check for imports of ch.qos.logback.classic.Logger
                        boolean hasLoggerImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("ch.qos.logback.classic.Logger"));
                        
                        if (hasLoggerImport) {
                            LogbackTransformer transformer = new LogbackTransformer();
                            cu.accept(transformer, null);
                            
                            if (transformer.wasModified()) {
                                modified = true;
                                
                                // Remove the problematic import
                                cu.getImports().removeIf(imp -> imp.getNameAsString().equals("ch.qos.logback.classic.Logger"));
                                
                                // Add import for Method if needed
                                boolean hasMethodImport = cu.getImports().stream()
                                    .anyMatch(imp -> imp.getNameAsString().equals("java.lang.reflect.Method"));
                                if (!hasMethodImport) {
                                    cu.addImport("java.lang.reflect.Method");
                                }
                            }
                        }
                        
                        if (modified) {
                            // Write back the transformed file
                            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
                            String newContent = printer.print(cu);
                            
                            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                                writer.write(newContent);
                                transformedFiles.add(filePath);
                                System.out.println("✓ Transformed: " + filePath);
                            }
                        }
                        
                    } catch (Exception e) {
                        System.err.println("✗ Error processing " + filePath + ": " + e.getMessage());
                    }
                });
            
            System.out.println("\n=== TRANSFORMATION COMPLETE ===");
            System.out.println("Transformed " + transformedFiles.size() + " file(s)");
            
            if (!transformedFiles.isEmpty()) {
                System.out.println("\nAPPLICATION INSTRUCTIONS:");
                System.out.println("1. This transformation fixes compilation errors caused by logback-classic 1.4.5");
                System.out.println("2. The fix uses reflection to avoid the missing LoggingEventAware interface");
                System.out.println("3. For production code, consider upgrading SLF4J to 2.0.0+ instead");
                System.out.println("\nThe transformation is generic and can be applied to any project");
                System.out.println("with the same logback-classic 1.4.5 / SLF4J < 2.0.0 compatibility issue.");
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class LogbackTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        private String loggerVarName = null;
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Pattern: Logger logger = (Logger) LoggerFactory.getLogger(...);
            if (n.getVariables().size() == 1) {
                VariableDeclarator declarator = n.getVariables().get(0);
                
                if (declarator.getType().toString().equals("Logger")) {
                    Optional<Expression> initializer = declarator.getInitializer();
                    if (initializer.isPresent() && initializer.get() instanceof CastExpr) {
                        CastExpr cast = (CastExpr) initializer.get();
                        if (cast.getType().toString().equals("Logger")) {
                            // Change type from Logger to Object
                            declarator.setType(new ClassOrInterfaceType("Object"));
                            // Remove the cast
                            declarator.setInitializer(cast.getExpression());
                            loggerVarName = declarator.getNameAsString();
                            modified = true;
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Pattern: logger.setLevel(...) or logger.addAppender(...)
            String methodName = n.getNameAsString();
            if ((methodName.equals("setLevel") || methodName.equals("addAppender")) && 
                n.getScope().isPresent()) {
                
                Expression scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    String varName = ((NameExpr) scope).getNameAsString();
                    
                    // Check if this is our logger variable (or any logger variable)
                    // We'll transform any calls to setLevel/addAppender on variables
                    // that were previously typed as Logger
                    
                    // Create combined reflective call for both methods
                    // We'll replace the entire block containing these calls
                    Node parent = n.getParentNode().orElse(null);
                    if (parent instanceof ExpressionStmt) {
                        // Look for the method declaration or initialization block
                        // that contains these calls
                        // For simplicity, we'll transform each call individually
                        // but a better approach would transform the whole setup block
                        
                        String paramType = methodName.equals("setLevel") ? "Level.class" : "Appender.class";
                        String argValue = n.getArguments().isEmpty() ? "" : n.getArguments().get(0).toString();
                        
                        String reflectiveCall = 
                            "try {\n" +
                            "    if (" + varName + ".getClass().getName().equals(\"ch.qos.logback.classic.Logger\")) {\n" +
                            "        Method " + methodName + "Method = " + varName + ".getClass().getMethod(\"" + methodName + "\", " + paramType + ");\n" +
                            "        " + methodName + "Method.invoke(" + varName + ", " + argValue + ");\n" +
                            "    }\n" +
                            "} catch (Exception e) {\n" +
                            "    // Ignore - might not be using logback\n" +
                            "}";
                        
                        try {
                            JavaParser parser = new JavaParser();
                            TryStmt tryStmt = parser.parseStatement(reflectiveCall).getResult().orElseThrow().asTryStmt();
                            ((ExpressionStmt) parent).replace(tryStmt);
                            modified = true;
                        } catch (Exception e) {
                            System.err.println("Failed to parse reflective call: " + e.getMessage());
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}