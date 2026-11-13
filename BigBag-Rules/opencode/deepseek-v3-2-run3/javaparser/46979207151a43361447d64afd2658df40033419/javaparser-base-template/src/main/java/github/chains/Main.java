package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming JAXBToStringStrategy.getInstance() calls in: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int transformedFiles = 0;
            int transformedCalls = 0;
            
            for (Path javaFile : javaFiles) {
                boolean fileModified = false;
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                
                if (cu != null) {
                    // Count transformations before modifying
                    int transformationsInFile = countTransformations(cu);
                    
                    if (transformationsInFile > 0) {
                        System.out.println("Found " + transformationsInFile + " transformations in " + javaFile);
                        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
                            if (isJAXBToStringStrategyGetInstanceCall(methodCall)) {
                                methodCall.getScope().ifPresent(scope -> {
                                    System.out.println("Transforming: " + methodCall);
                                    FieldAccessExpr fieldAccess = new FieldAccessExpr(
                                        scope,
                                        "INSTANCE"
                                    );
                                    methodCall.replace(fieldAccess);
                                });
                            }
                        });
                        
                        String transformedContent = cu.toString();
                        Files.write(javaFile, transformedContent.getBytes());
                        fileModified = true;
                        transformedCalls += transformationsInFile;
                    }
                }
                
                if (fileModified) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformation complete!");
            System.out.println("Files modified: " + transformedFiles);
            System.out.println("JAXBToStringStrategy.getInstance() calls transformed: " + transformedCalls);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean isJAXBToStringStrategyGetInstanceCall(MethodCallExpr methodCall) {
        if (!methodCall.getNameAsString().equals("getInstance")) {
            return false;
        }
        
        return methodCall.getScope()
            .map(scope -> {
                String scopeStr = scope.toString();
                return scopeStr.equals("JAXBToStringStrategy") ||
                       scopeStr.equals("org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy") ||
                       scopeStr.endsWith(".JAXBToStringStrategy");
            })
            .orElse(false);
    }
    
    private static int countTransformations(CompilationUnit cu) {
        return (int) cu.findAll(MethodCallExpr.class)
            .stream()
            .filter(Main::isJAXBToStringStrategyGetInstanceCall)
            .count();
    }
}
