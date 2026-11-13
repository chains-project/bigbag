package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration parameters - these would be passed as arguments in a real tool
    private static final String OLD_LOGGER_CLASS = "ch.qos.logback.classic.Logger";
    private static final String OLD_LEVEL_CLASS = "ch.qos.logback.classic.Level";
    private static final String APPENDER_CLASS = "ch.qos.logback.core.Appender";
    private static final String ILOGGING_EVENT_CLASS = "ch.qos.logback.classic.spi.ILoggingEvent";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            transformProject(Paths.get(sourceDir));
            System.out.println("Transformation complete!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws Exception {
        List<File> javaFiles = new ArrayList<>();
        collectJavaFiles(sourceDir.toFile(), javaFiles);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int transformedFiles = 0;
        for (File file : javaFiles) {
            if (transformFile(file)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
    }
    
    private static void collectJavaFiles(File dir, List<File> javaFiles) {
        if (dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    collectJavaFiles(file, javaFiles);
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
    }
    
    private static boolean transformFile(File file) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + file);
            return false;
        }
        
        LogbackTransformerVisitor visitor = new LogbackTransformerVisitor();
        visitor.visit(cu, null);
        
        if (visitor.isModified()) {
            // Write the transformed file
            cu.getStorage().ifPresentOrElse(
                storage -> {
                    try {
                        storage.save();
                    } catch (Exception e) {
                        System.err.println("Failed to save: " + file + ", error: " + e.getMessage());
                    }
                },
                () -> {
                    try {
                        java.nio.file.Files.write(file.toPath(), cu.toString().getBytes());
                    } catch (Exception e) {
                        System.err.println("Failed to write: " + file + ", error: " + e.getMessage());
                    }
                }
            );
            return true;
        }
        
        return false;
    }
    
    private static class LogbackTransformerVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public void visit(ImportDeclaration id, Void arg) {
            super.visit(id, arg);
            
            String importName = id.getNameAsString();
            
            // Check for problematic logback imports that may cause compatibility issues
            // with SLF4J 1.7.x when using logback 1.4.4
            if (importName.equals(OLD_LOGGER_CLASS) || 
                importName.equals(OLD_LEVEL_CLASS) ||
                importName.equals(APPENDER_CLASS) ||
                importName.equals(ILOGGING_EVENT_CLASS)) {
                
                System.out.println("Found potentially problematic import: " + importName);
                // We could remove or replace these imports, but for now just warn
                // In a real transformation, we might replace with SLF4J equivalents
                // or add conditional logic
                
                modified = true;
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceType type, Void arg) {
            super.visit(type, arg);
            
            String typeName = type.getNameAsString();
            
            // Check for usage of logback Logger type in casts or declarations
            // This is where the compatibility issue manifests
            if (typeName.equals("Logger") && type.getScope().isPresent()) {
                String scope = type.getScope().get().asString();
                if (scope.equals("ch.qos.logback.classic")) {
                    System.out.println("Found usage of ch.qos.logback.classic.Logger type");
                    // This type now implements LoggingEventAware which may not exist
                    // in older SLF4J versions
                    
                    // Option 1: Replace with org.slf4j.Logger if possible
                    // Option 2: Add @SuppressWarnings or workaround
                    // For now, we'll just detect it
                    
                    modified = true;
                }
            }
        }
        
        @Override
        public void visit(CastExpr cast, Void arg) {
            super.visit(cast, arg);
            
            // Check for casts to ch.qos.logback.classic.Logger
            if (cast.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = cast.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("Logger") && type.getScope().isPresent()) {
                    String scope = type.getScope().get().asString();
                    if (scope.equals("ch.qos.logback.classic")) {
                        System.out.println("Found cast to ch.qos.logback.classic.Logger");
                        
                        // This cast will fail if LoggingEventAware is not in classpath
                        // Replace with reflection-based approach
                        replaceLoggerCastWithReflection(cast);
                        
                        modified = true;
                    }
                }
            }
        }
        
        private void replaceLoggerCastWithReflection(CastExpr cast) {
            // Get the expression being cast (e.g., LoggerFactory.getLogger(...))
            com.github.javaparser.ast.expr.Expression expr = cast.getExpression();
            
            // Create a reflective approach
            // Instead of: (Logger) LoggerFactory.getLogger(X.class)
            // We'll use: getLoggerReflective(X.class)
            
            // But we need to check the context - is this followed by setLevel/addAppender?
            // First, let's just add a wrapper method call suggestion
            
            cast.replace(new com.github.javaparser.ast.expr.MethodCallExpr(
                new com.github.javaparser.ast.expr.NameExpr("LogbackCompat"),
                "getLoggerSafely",
                new NodeList<>(expr.clone())
            ));
            
            // Also need to add import for LogbackCompat if not present
            // and create the utility class
        }
        
        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);
            
            // Look for test setup methods that might configure logback
            if (method.getNameAsString().equals("setUp") || 
                method.getNameAsString().equals("setUpEach") ||
                method.getNameAsString().equals("beforeEach") ||
                method.getNameAsString().equals("before")) {
                
                // Check for logback configuration in test setup
                method.getBody().ifPresent(body -> {
                    checkForLogbackConfiguration(body);
                });
            }
        }
        
        private void checkForLogbackConfiguration(BlockStmt body) {
            // Look for common logback test patterns:
            // 1. LoggerFactory.getLogger() cast to ch.qos.logback.classic.Logger
            // 2. Calls to setLevel()
            // 3. Calls to addAppender()
            
            // This is a simple check - a real implementation would need
            // more sophisticated pattern matching
            
            String bodyStr = body.toString();
            if (bodyStr.contains("LoggerFactory.getLogger") && 
                (bodyStr.contains("setLevel") || bodyStr.contains("addAppender"))) {
                System.out.println("Found potential logback test configuration");
                
                // Add warning comment
                body.setComment(new com.github.javaparser.ast.comments.LineComment(
                    " NOTE: Logback test configuration may need updating for logback 1.4.4" +
                    " with SLF4J 1.7.x due to LoggingEventAware interface"
                ));
                
                modified = true;
            }
        }
    }
}