package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean transformFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String originalContent = content;
            
            // Remove import ch.qos.logback.classic.Logger
            content = content.replace("import ch.qos.logback.classic.Logger;", "");
            
            // Replace Logger.ROOT_LOGGER_NAME with "ROOT"
            content = content.replace("Logger.ROOT_LOGGER_NAME", "\"ROOT\"");
            
            // Replace ((Logger) LoggerFactory.getLogger(...)).setLevel(...) pattern
            // This is a simple regex approach - in a real implementation we'd use proper AST
            content = content.replaceAll("\\(\\(Logger\\)\\s*LoggerFactory\\.getLogger\\([^)]+\\)\\)\\.setLevel\\(([^)]+)\\)",
                "try { Object logger = LoggerFactory.getLogger(\"ROOT\"); logger.getClass().getMethod(\"setLevel\", Class.forName(\"ch.qos.logback.classic.Level\")).invoke(logger, $1); } catch (Exception e) { e.printStackTrace(); }");
            
            if (!content.equals(originalContent)) {
                Files.write(filePath, content.getBytes());
                System.out.println("Transformed: " + filePath);
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error transforming " + filePath + ": " + e.getMessage());
            return false;
        }
    }
}