package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation for logback 1.4.5 compatibility issue.
 * 
 * Breaking change: ch.qos.logback.classic.Logger implements
 * org.slf4j.spi.LoggingEventAware (slf4j 2.0.x interface).
 * 
 * When using slf4j 1.7.x, this causes compilation error:
 * "cannot access org.slf4j.spi.LoggingEventAware"
 * 
 * Transformation replaces problematic patterns with reflective access.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-dir>");
            System.err.println("Transforms code using ch.qos.logback.classic.Logger for slf4j 1.7.x compatibility");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing: " + sourceDir.toAbsolutePath());
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int modifiedCount = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                if (cu == null) continue;
                
                LogbackFixer fixer = new LogbackFixer();
                fixer.visit(cu, null);
                
                if (fixer.isModified()) {
                    // Add import for helper if needed
                    boolean hasHelperImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals("github.chains.LogbackReflectionHelper"));
                    if (!hasHelperImport) {
                        cu.addImport("github.chains.LogbackReflectionHelper");
                    }
                    
                    Files.write(javaFile, cu.toString().getBytes());
                    modifiedCount++;
                    System.out.println("Modified: " + sourceDir.relativize(javaFile));
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nDone. Modified " + modifiedCount + " files.");
        if (modifiedCount > 0) {
            System.out.println("\nNote: This transformation adds dependency on LogbackReflectionHelper class.");
            System.out.println("The helper class uses reflection to avoid compile-time dependency on");
            System.out.println("org.slf4j.spi.LoggingEventAware interface (slf4j 2.0.x).");
        }
    }
    
    static class LogbackFixer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Remove problematic import
            if (n.getNameAsString().equals("ch.qos.logback.classic.Logger")) {
                n.remove();
                modified = true;
            }
        }
        
        @Override
        public void visit(CastExpr n, Void arg) {
            super.visit(n, arg);
            
            // Pattern: (Logger) LoggerFactory.getLogger(...)
            if (n.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = n.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("Logger")) {
                    processLoggerCast(n);
                }
            }
        }
        
        private void processLoggerCast(CastExpr cast) {
            Node parent = cast.getParentNode().orElse(null);
            if (!(parent instanceof MethodCallExpr)) return;
            
            MethodCallExpr methodCall = (MethodCallExpr) parent;
            String methodName = methodCall.getNameAsString();
            
            // Check for logback-specific methods
            List<String> logbackMethods = List.of(
                "setLevel", "addAppender", "detachAppender",
                "detachAndStopAllAppenders", "callAppenders"
            );
            
            if (logbackMethods.contains(methodName) && 
                cast.getExpression() instanceof MethodCallExpr) {
                
                MethodCallExpr getLoggerCall = (MethodCallExpr) cast.getExpression();
                if ("getLogger".equals(getLoggerCall.getNameAsString())) {
                    replaceWithReflectiveCall(methodCall, getLoggerCall, methodName);
                }
            }
        }
        
        private void replaceWithReflectiveCall(MethodCallExpr originalCall,
                                              MethodCallExpr getLoggerCall,
                                              String methodName) {
            // Build: LogbackReflectionHelper.invoke(getLoggerCall, methodName, args...)
            MethodCallExpr reflectiveCall = new MethodCallExpr();
            reflectiveCall.setScope(new NameExpr("LogbackReflectionHelper"));
            reflectiveCall.setName("invoke");
            
            // First arg: getLogger argument (class or name)
            reflectiveCall.addArgument(getLoggerCall.getArgument(0).clone());
            
            // Second arg: method name
            reflectiveCall.addArgument(new com.github.javaparser.ast.expr.StringLiteralExpr(methodName));
            
            // Remaining args: original method arguments
            originalCall.getArguments().forEach(arg -> 
                reflectiveCall.addArgument(arg.clone()));
            
            originalCall.replace(reflectiveCall);
            modified = true;
        }
    }
}