package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class BatchMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);
        
        List<File> javaFiles = findJavaFiles(sourceDirectory);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        for (File javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Successfully transformed " + transformedFiles + " files");
    }
    
    private static List<File> findJavaFiles(String directory) throws Exception {
        return Files.walk(Paths.get(directory))
                .filter(path -> path.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
    }
    
    private static boolean transformFile(File javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new FileInputStream(javaFile)).getResult().orElse(null);
            
            if (cu == null) {
                return false;
            }
            
            // Use an array to work around lambda variable capture limitation
            final boolean[] wasModified = {false};
            
            // Find all setLevel method calls
            cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
                if (methodCall.getNameAsString().equals("setLevel")) {
                    // Check the scope type
                    if (methodCall.getScope().isPresent()) {
                        String scopeStr = methodCall.getScope().get().toString();
                        
                        // Check if the scope contains a cast to Logger
                        if (scopeStr.contains("(Logger)") && scopeStr.contains("LoggerFactory.getLogger")) {
                            System.out.println("Found problematic setLevel call in " + javaFile.getName() + ": " + methodCall);
                            
                            // Get the parent statement
                            Statement parentStatement = methodCall.findAncestor(Statement.class).orElse(null);
                            if (parentStatement != null) {
                                // Extract the getLogger call and setLevel arguments
                                String getLoggerCall = extractGetterCall(scopeStr);
                                String setLevelArgs = extractSetLevelArgs(methodCall);
                                
                                // Create replacement - use Object instead of org.slf4j.Logger to avoid interface issues
                                String replacement = "{\n    Object loggerTemp = " + getLoggerCall + ";\n    if (loggerTemp instanceof ch.qos.logback.classic.Logger) {\n        ((ch.qos.logback.classic.Logger) loggerTemp).setLevel(" + setLevelArgs + ");\n    }\n}";
                                
                                try {
                                    BlockStmt replacementBlock = parser.parseBlock(replacement).getResult().orElse(null);
                                    if (replacementBlock != null) {
                                        parentStatement.replace(replacementBlock);
                                        System.out.println("  Successfully replaced!");
                                        wasModified[0] = true;
                                    }
                                } catch (Exception e) {
                                    System.err.println("  Error replacing: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            });
            
            if (wasModified[0]) {
                try (FileOutputStream fos = new FileOutputStream(javaFile)) {
                    fos.write(cu.toString().getBytes());
                }
                System.out.println("Transformed: " + javaFile.getAbsolutePath());
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile.getAbsolutePath() + ": " + e.getMessage());
        }
        
        return false;
    }
    
    private static String extractGetterCall(String scopeStr) {
        // Extract the LoggerFactory.getLogger(...) part
        int start = scopeStr.indexOf("LoggerFactory.getLogger");
        int end = scopeStr.indexOf(')', start) + 1;
        return scopeStr.substring(start, end);
    }
    
    private static String extractSetLevelArgs(MethodCallExpr methodCall) {
        // Extract the setLevel arguments
        return methodCall.getArguments().stream()
                .map(arg -> arg.toString())
                .collect(Collectors.joining(", "));
    }
}