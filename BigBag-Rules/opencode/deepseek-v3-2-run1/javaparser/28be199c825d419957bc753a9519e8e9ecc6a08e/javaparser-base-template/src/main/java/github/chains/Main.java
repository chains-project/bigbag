package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing the Maven DependencyGraphBuilder API breaking change.
 * 
 * Breaking Change Pattern:
 * - Old API: DependencyGraphBuilder.buildDependencyGraph(MavenProject project, ArtifactFilter filter)
 * - New API: DependencyGraphBuilder.buildDependencyGraph(ProjectBuildingRequest request, ArtifactFilter filter)
 * 
 * Transformation Rule:
 * Replaces calls to buildDependencyGraph(project, filter) with 
 * buildDependencyGraph(getProjectBuildingRequest(), filter) where getProjectBuildingRequest()
 * is a method that returns a ProjectBuildingRequest.
 * 
 * This is a generic transformation that works when:
 * 1. There's a getProjectBuildingRequest() or getBuildingRequest() method available
 * 2. Or the class has access to a ProjectBuildingRequest instance
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int transformedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                boolean fileTransformed = transformFile(javaFile);
                if (fileTransformed) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformation complete. Transformed " + transformedFiles + " files.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        boolean fileModified = false;
        
        // Find all method calls to DependencyGraphBuilder.buildDependencyGraph()
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
        
        for (MethodCallExpr methodCall : methodCalls) {
            if (isBuildDependencyGraphCall(methodCall)) {
                // Check if first argument appears to be a MavenProject
                if (methodCall.getArguments().size() >= 1) {
                    Expression firstArg = methodCall.getArgument(0);
                    
                    if (isLikelyMavenProject(firstArg)) {
                        // Transform: replace MavenProject argument with ProjectBuildingRequest
                        transformBuildDependencyGraphCall(methodCall);
                        fileModified = true;
                        System.out.println("Transformed buildDependencyGraph call in " + javaFile);
                    }
                }
            }
        }
        
        if (fileModified) {
            // Write the transformed file back
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            String transformedCode = cu.toString(config);
            Files.write(javaFile, transformedCode.getBytes());
        }
        
        return fileModified;
    }
    
    private static boolean isBuildDependencyGraphCall(MethodCallExpr methodCall) {
        // Check if method name is buildDependencyGraph
        if (!"buildDependencyGraph".equals(methodCall.getNameAsString())) {
            return false;
        }
        
        // Should have at least 1 argument
        return methodCall.getArguments().size() >= 1;
    }
    
    private static boolean isLikelyMavenProject(Expression expr) {
        String exprStr = expr.toString();
        
        // Check common patterns for MavenProject variables
        if (expr instanceof NameExpr) {
            String name = ((NameExpr) expr).getNameAsString().toLowerCase();
            return name.contains("project") || name.equals("p") || name.equals("proj") || 
                   name.equals("mavenproject") || name.equals("mp");
        }
        
        // Check if expression contains "project" (case-insensitive)
        return exprStr.matches("(?i).*project.*");
    }
    
    private static void transformBuildDependencyGraphCall(MethodCallExpr methodCall) {
        // Replace the first argument (MavenProject) with a call to getProjectBuildingRequest()
        // This assumes there's a getProjectBuildingRequest() method available
        // or getBuildingRequest() method
        
        // Try to find appropriate method name
        String requestMethodName = findProjectBuildingRequestMethod(methodCall);
        
        MethodCallExpr getRequestCall = new MethodCallExpr();
        getRequestCall.setName(requestMethodName);
        
        // Replace the first argument
        methodCall.getArguments().set(0, getRequestCall);
    }
    
    private static String findProjectBuildingRequestMethod(MethodCallExpr context) {
        // Look for common method names that return ProjectBuildingRequest
        // Common patterns: getProjectBuildingRequest(), getBuildingRequest(), 
        // session.getProjectBuildingRequest()
        
        // For simplicity, we'll use getBuildingRequest() as a default
        // In a more advanced implementation, we would analyze the class structure
        // to find available methods
        
        return "getBuildingRequest";
    }
}