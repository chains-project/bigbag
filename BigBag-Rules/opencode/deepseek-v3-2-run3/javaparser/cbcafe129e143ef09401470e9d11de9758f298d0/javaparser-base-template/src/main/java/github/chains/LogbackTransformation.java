package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic transformation to fix compilation errors caused by logback-classic 1.4.6
 * requiring SLF4J 2.x when used with SLF4J 1.x.
 * 
 * Breaking change: ch.qos.logback.classic.Logger implements 
 * org.slf4j.spi.LoggingEventAware (SLF4J 2.x), which doesn't exist in SLF4J 1.x.
 * 
 * Old API pattern: Using ch.qos.logback.classic.Logger directly
 * New API pattern: Use org.slf4j.Logger with reflection for logback-specific methods
 * 
 * Transformation handles:
 * 1. Variable declarations of type ch.qos.logback.classic.Logger
 * 2. Casts to ch.qos.logback.classic.Logger  
 * 3. Calls to setLevel() and addAppender() on Logger instances
 */
public class LogbackTransformation {
    
    public static void transformDirectory(Path sourceDir) throws Exception {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Processing " + javaFiles.size() + " Java files in " + sourceDir);
        
        JavaParser parser = new JavaParser();
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                LogbackFixer fixer = new LogbackFixer();
                fixer.visit(cu, null);
                
                if (fixer.changesMade) {
                    // Add import for org.slf4j.Logger if needed
                    boolean hasSlf4jLoggerImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals("org.slf4j.Logger"));
                    if (!hasSlf4jLoggerImport && fixer.needsSlf4jLoggerImport) {
                        cu.addImport("org.slf4j.Logger");
                    }
                    
                    Files.write(javaFile, cu.toString().getBytes());
                    System.out.println("  Fixed: " + sourceDir.relativize(javaFile));
                    filesModified++;
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Total files fixed: " + filesModified);
    }
    
    static class LogbackFixer extends ModifierVisitor<Void> {
        boolean changesMade = false;
        boolean needsSlf4jLoggerImport = false;
        
        @Override
        public Node visit(VariableDeclarator varDecl, Void arg) {
            // Check for variables declared as Logger (ch.qos.logback.classic.Logger)
            if (varDecl.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = varDecl.getType().asClassOrInterfaceType();
                if ("Logger".equals(type.getNameAsString())) {
                    // Change to org.slf4j.Logger
                    changesMade = true;
                    needsSlf4jLoggerImport = true;
                    ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "org.slf4j.Logger");
                    varDecl.setType(newType);
                }
            }
            return (Node) super.visit(varDecl, arg);
        }
        
        @Override
        public Node visit(CastExpr castExpr, Void arg) {
            // Check for casts to Logger (ch.qos.logback.classic.Logger)
            if (castExpr.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = castExpr.getType().asClassOrInterfaceType();
                if ("Logger".equals(type.getNameAsString())) {
                    // Check if casting LoggerFactory.getLogger(...)
                    if (castExpr.getExpression().isMethodCallExpr()) {
                        MethodCallExpr methodCall = castExpr.getExpression().asMethodCallExpr();
                        if ("getLogger".equals(methodCall.getNameAsString())) {
                            // Remove the cast - result is org.slf4j.Logger
                            changesMade = true;
                            return castExpr.getExpression();
                        }
                    }
                }
            }
            return (Node) super.visit(castExpr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr methodCall, Void arg) {
            // Check for setLevel() or addAppender() calls
            String methodName = methodCall.getNameAsString();
            if (("setLevel".equals(methodName) || "addAppender".equals(methodName)) && 
                methodCall.getScope().isPresent()) {
                
                Expression scope = methodCall.getScope().get();
                String varName = scope.toString();
                NodeList<Expression> args = methodCall.getArguments();
                
                // Generate reflection-based replacement
                String reflectionCode = generateReflectionCode(methodName, varName, args);
                
                try {
                    JavaParser parser = new JavaParser();
                    BlockStmt block = parser.parseBlock(reflectionCode).getResult().orElse(null);
                    if (block != null) {
                        changesMade = true;
                        
                        // Replace the entire expression statement with the block
                        Optional<Node> parent = methodCall.getParentNode();
                        if (parent.isPresent() && parent.get() instanceof ExpressionStmt) {
                            return block;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to generate reflection code: " + e.getMessage());
                }
            }
            
            return (Node) super.visit(methodCall, arg);
        }
        
        private String generateReflectionCode(String methodName, String varName, NodeList<Expression> args) {
            StringBuilder code = new StringBuilder();
            code.append("try {\n");
            code.append("  Class<?> loggerClass = Class.forName(\"ch.qos.logback.classic.Logger\");\n");
            code.append("  if (loggerClass.isInstance(").append(varName).append(")) {\n");
            code.append("    Object logbackLogger = loggerClass.cast(").append(varName).append(");\n");
            
            if ("setLevel".equals(methodName)) {
                code.append("    java.lang.reflect.Method setLevel = loggerClass.getMethod(\"setLevel\", ")
                     .append("Class.forName(\"ch.qos.logback.classic.Level\"));\n");
                code.append("    setLevel.invoke(logbackLogger, ").append(args.get(0).toString()).append(");\n");
            } else { // addAppender
                code.append("    java.lang.reflect.Method addAppender = loggerClass.getMethod(\"addAppender\", ")
                     .append("Class.forName(\"ch.qos.logback.core.Appender\"));\n");
                code.append("    addAppender.invoke(logbackLogger, ").append(args.get(0).toString()).append(");\n");
            }
            
            code.append("  }\n");
            code.append("} catch (Exception e) {\n");
            code.append("  // Logging configuration failed (compatibility with SLF4J 1.x and logback 1.4.6)\n");
            code.append("  e.printStackTrace();\n");
            code.append("}");
            
            return code.toString();
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java LogbackTransformation <source-directory>");
            System.out.println("\nApplies transformation to fix logback-classic 1.4.6 compatibility");
            System.out.println("with SLF4J 1.x by:");
            System.out.println("1. Changing Logger variable types to org.slf4j.Logger");
            System.out.println("2. Removing casts to ch.qos.logback.classic.Logger");
            System.out.println("3. Replacing setLevel()/addAppender() with reflection");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        transformDirectory(sourceDir);
    }
}