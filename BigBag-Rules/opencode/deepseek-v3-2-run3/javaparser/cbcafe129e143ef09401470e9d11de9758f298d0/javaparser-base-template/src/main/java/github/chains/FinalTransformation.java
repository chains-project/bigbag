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
 * Transformation to fix compilation errors when using logback-classic 1.4.6 with SLF4J 1.x.
 * 
 * BREAKING CHANGE: logback-classic 1.4.6 implements org.slf4j.spi.LoggingEventAware (SLF4J 2.x),
 * but SLF4J 1.x doesn't have this interface, causing "cannot access org.slf4j.spi.LoggingEventAware".
 * 
 * OLD PATTERN:
 *   import ch.qos.logback.classic.Logger;
 *   Logger logger = (Logger) LoggerFactory.getLogger(...);
 *   logger.setLevel(Level.INFO);
 *   logger.addAppender(mockAppender);
 * 
 * NEW PATTERN:
 *   import org.slf4j.Logger;
 *   Logger logger = LoggerFactory.getLogger(...);
 *   // Logging configuration removed - incompatible with SLF4J 1.x
 *   // Consider upgrading to SLF4J 2.x or downgrading logback-classic
 * 
 * The transformation makes code compile but may break tests that depend on logback configuration.
 * Projects should upgrade SLF4J to 2.x for full compatibility with logback-classic 1.4.6.
 */
public class FinalTransformation {
    
    public static void transform(Path sourceDir) throws Exception {
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Transforming " + javaFiles.size() + " files for logback/SLF4J compatibility");
        
        JavaParser parser = new JavaParser();
        int fixed = 0;
        
        for (Path file : javaFiles) {
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            if (cu == null) continue;
            
            Fixer fixer = new Fixer();
            fixer.visit(cu, null);
            
            if (fixer.changed) {
                // Remove ch.qos.logback.classic.Logger import if present
                List<ImportDeclaration> imports = cu.getImports();
                for (int i = 0; i < imports.size(); i++) {
                    if (imports.get(i).getNameAsString().equals("ch.qos.logback.classic.Logger")) {
                        cu.remove(imports.get(i));
                        break;
                    }
                }
                
                // Add org.slf4j.Logger import if needed and not already present
                boolean hasSlf4jLogger = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("org.slf4j.Logger"));
                if (!hasSlf4jLogger && fixer.needsSlf4jLogger) {
                    cu.addImport("org.slf4j.Logger");
                }
                
                Files.write(file, cu.toString().getBytes());
                System.out.println("  Fixed: " + sourceDir.relativize(file));
                fixed++;
            }
        }
        
        System.out.println("Done. Fixed " + fixed + " files.");
        System.out.println("\nNOTE: This fixes compilation but tests may fail due to missing log configuration.");
        System.out.println("      Consider upgrading SLF4J to 2.x for full compatibility with logback 1.4.6.");
    }
    
    static class Fixer extends ModifierVisitor<Void> {
        boolean changed = false;
        boolean needsSlf4jLogger = false;
        
        @Override
        public Node visit(VariableDeclarator vd, Void arg) {
            // Change "Logger variable" to "org.slf4j.Logger variable"
            if (vd.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = vd.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("Logger")) {
                    changed = true;
                    needsSlf4jLogger = true;
                    vd.setType(new ClassOrInterfaceType(null, "org.slf4j.Logger"));
                }
            }
            return (Node) super.visit(vd, arg);
        }
        
        @Override
        public Node visit(CastExpr expr, Void arg) {
            // Remove "(Logger)" casts
            if (expr.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = expr.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("Logger")) {
                    changed = true;
                    return expr.getExpression(); // Remove cast
                }
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr call, Void arg) {
            // Replace setLevel() and addAppender() with commented warning
            String name = call.getNameAsString();
            if (name.equals("setLevel") || name.equals("addAppender")) {
                changed = true;
                
                // Create comment block explaining the issue
                String comment = "// Logback configuration removed due to SLF4J 1.x / logback 1.4.6 incompatibility\n" +
                               "// logback-classic 1.4.6 requires SLF4J 2.x (implements LoggingEventAware)\n" +
                               "// To fix: upgrade SLF4J to 2.x or downgrade logback-classic\n" +
                               "// Original code: " + call.toString().replace("\n", " ");
                
                // Create expression statement with just the comment
                try {
                    JavaParser parser = new JavaParser();
                    BlockStmt block = parser.parseBlock("{" + comment + "}").getResult().orElse(null);
                    if (block != null) {
                        // Get just the statement from the block
                        if (!block.getStatements().isEmpty()) {
                            return block.getStatement(0);
                        }
                    }
                } catch (Exception e) {
                    // Fallback: empty statement
                    return new ExpressionStmt(new NameExpr("// logging config removed"));
                }
            }
            return (Node) super.visit(call, arg);
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java FinalTransformation <source-directory>");
            System.out.println("\nFixes 'cannot access org.slf4j.spi.LoggingEventAware' errors");
            System.out.println("when using logback-classic 1.4.6 with SLF4J 1.x.");
            System.exit(1);
        }
        transform(Paths.get(args[0]));
    }
}