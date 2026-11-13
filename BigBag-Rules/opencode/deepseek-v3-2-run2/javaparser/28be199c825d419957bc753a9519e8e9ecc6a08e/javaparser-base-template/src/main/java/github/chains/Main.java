package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation rule for Maven dependency tree API breaking change.
 * 
 * Transforms calls from:
 *   DependencyGraphBuilder.buildDependencyGraph(MavenProject, ArtifactFilter)
 * to:
 *   DependencyGraphBuilder.buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter)
 * 
 * This transformation replaces MavenProject parameters with ProjectBuildingRequest
 * parameters at call sites of the buildDependencyGraph method.
 */
public class Main {
    
    // Configuration parameters - these should be changed based on the specific project
    private static final String DEPENDENCY_GRAPH_BUILDER_TYPE = "org.apache.maven.shared.dependency.graph.DependencyGraphBuilder";
    private static final String BUILD_DEPENDENCY_GRAPH_METHOD = "buildDependencyGraph";
    private static final String MAVEN_PROJECT_TYPE = "org.apache.maven.project.MavenProject";
    private static final String PROJECT_BUILDING_REQUEST_TYPE = "org.apache.maven.project.ProjectBuildingRequest";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying transformation to: " + sourceDirectory);
        
        try {
            transformProject(Paths.get(sourceDirectory));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws IOException {
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser(new ParserConfiguration());
            CompilationUnit cu = parser.parse(filePath.toFile()).getResult().orElseThrow();
            
            BuildDependencyGraphTransformer transformer = new BuildDependencyGraphTransformer();
            cu.accept(transformer, null);
            
            System.out.println("Processed: " + filePath);
            Files.write(filePath, cu.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms buildDependencyGraph method calls.
     * Replaces MavenProject parameters with ProjectBuildingRequest parameters.
     */
    private static class BuildDependencyGraphTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a buildDependencyGraph method call
            if (isBuildDependencyGraphCall(n)) {
                // Get the arguments
                List<Expression> arguments = n.getArguments();
                
                // We expect exactly 2 arguments: MavenProject and ArtifactFilter
                if (arguments.size() == 2) {
                    Expression firstArg = arguments.get(0);
                    
                    // Replace the first argument (MavenProject) with a ProjectBuildingRequest
                    // This is a generic transformation - the actual conversion logic
                    // would depend on the project's available methods
                    MethodCallExpr replacement = createProjectBuildingRequestConversion(firstArg);
                    
                    if (replacement != null) {
                        n.setArgument(0, replacement);
                        return super.visit(n, arg);
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isBuildDependencyGraphCall(MethodCallExpr n) {
            String methodName = n.getNameAsString();
            return BUILD_DEPENDENCY_GRAPH_METHOD.equals(methodName);
        }
        
        /**
         * Creates a method call to convert MavenProject to ProjectBuildingRequest.
         * This is a generic implementation that tries common conversion patterns.
         */
        private MethodCallExpr createProjectBuildingRequestConversion(Expression mavenProjectExpr) {
            // Pattern 1: Use the existing getBuildingRequest() method from the class
            // This is the most common pattern when the class already has access to ProjectBuildingRequest
            MethodCallExpr getBuildingRequestCall = new MethodCallExpr();
            getBuildingRequestCall.setName("getBuildingRequest");
            
            // Pattern 2: If we need to create a new DefaultProjectBuildingRequest
            // This would require additional imports and might not be appropriate
            // for all projects, so we use pattern 1 as the default
            
            return getBuildingRequestCall;
        }
    }
}