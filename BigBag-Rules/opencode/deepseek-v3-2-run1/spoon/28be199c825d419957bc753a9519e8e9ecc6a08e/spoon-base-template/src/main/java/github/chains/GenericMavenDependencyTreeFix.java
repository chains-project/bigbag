package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

import java.util.List;
import java.util.ArrayList;

/**
 * Generic fix for maven-dependency-tree 3.1.0 API breaking change.
 * 
 * Breaking Change:
 * - Old: DependencyGraphBuilder.buildDependencyGraph(MavenProject project, ArtifactFilter filter)
 * - New: DependencyGraphBuilder.buildDependencyGraph(ProjectBuildingRequest request, ArtifactFilter filter)
 * 
 * This transformation provides a GENERIC, REUSABLE rule that can be applied to ANY Maven project
 * affected by this breaking change. The rule:
 * 1. Matches calls to buildDependencyGraph(MavenProject, ArtifactFilter)
 * 2. Replaces MavenProject argument with ProjectBuildingRequest
 * 3. Provides strategies for creating ProjectBuildingRequest from context
 */
public class GenericMavenDependencyTreeFix {
    
    private final Factory factory;
    private int transformationCount = 0;
    
    public GenericMavenDependencyTreeFix(Factory factory) {
        this.factory = factory;
    }
    
    public int getTransformationCount() {
        return transformationCount;
    }
    
    /**
     * Apply transformation to model.
     */
    public void transform(CtModel model) {
        List<CtInvocation<?>> invocations = findBuildDependencyGraphCalls(model);
        
        System.out.println("Found " + invocations.size() + " buildDependencyGraph calls");
        
        for (CtInvocation<?> invocation : invocations) {
            if (transformBuildDependencyGraphCall(invocation)) {
                transformationCount++;
            }
        }
        
        System.out.println("Transformed " + transformationCount + " calls");
    }
    
    /**
     * Find all buildDependencyGraph calls in model.
     */
    private List<CtInvocation<?>> findBuildDependencyGraphCalls(CtModel model) {
        List<CtInvocation<?>> result = new ArrayList<>();
        
        model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                return isBuildDependencyGraphCall(invocation);
            }
        }).forEach(result::add);
        
        return result;
    }
    
    /**
     * Check if invocation is a buildDependencyGraph call.
     * Uses structural matching on method name and approximate type checking.
     */
    private boolean isBuildDependencyGraphCall(CtInvocation<?> invocation) {
        CtExecutableReference<?> execRef = invocation.getExecutable();
        if (execRef == null) {
            return false;
        }
        
        String methodName = execRef.getSimpleName();
        if (!"buildDependencyGraph".equals(methodName)) {
            return false;
        }
        
        // Check declaring type - accept any type ending with DependencyGraphBuilder
        CtTypeReference<?> declaringType = execRef.getDeclaringType();
        if (declaringType == null) {
            return false;
        }
        
        String typeName = declaringType.getQualifiedName();
        return typeName != null && 
               (typeName.equals("org.apache.maven.shared.dependency.graph.DependencyGraphBuilder") ||
                typeName.endsWith(".DependencyGraphBuilder"));
    }
    
    /**
     * Transform a single buildDependencyGraph call.
     * Returns true if transformation was applied.
     */
    private boolean transformBuildDependencyGraphCall(CtInvocation<?> invocation) {
        List<CtExpression<?>> args = invocation.getArguments();
        if (args.size() < 2) {
            System.err.println("Warning: buildDependencyGraph call has " + args.size() + " arguments, expected at least 2");
            return false;
        }
        
        CtExpression<?> firstArg = args.get(0);
        CtExpression<?> secondArg = args.get(1);
        
        System.out.println("Transforming buildDependencyGraph call at: " + invocation.getPosition());
        System.out.println("  Old API: buildDependencyGraph(MavenProject, ArtifactFilter)");
        System.out.println("  New API: buildDependencyGraph(ProjectBuildingRequest, ArtifactFilter)");
        
        // Create replacement ProjectBuildingRequest expression
        // This is the key transformation logic
        CtExpression<?> projectBuildingRequestExpr = createProjectBuildingRequestExpression(firstArg, invocation);
        
        // Create new arguments list
        List<CtExpression<?>> newArgs = new ArrayList<>();
        newArgs.add(projectBuildingRequestExpr);
        newArgs.add(secondArg);
        
        // Add any additional arguments (for overloaded versions)
        for (int i = 2; i < args.size(); i++) {
            newArgs.add(args.get(i));
        }
        
        // Create new invocation using code snippet for simplicity
        // In a real implementation, we would properly reconstruct the invocation
        String newInvocationCode = createNewInvocationCode(invocation, projectBuildingRequestExpr, secondArg);
        CtCodeSnippetExpression<?> newInvocation = factory.createCodeSnippetExpression(newInvocationCode);
        
        // Replace old invocation with new one
        invocation.replace(newInvocation);
        
        return true;
    }
    
    /**
     * Create ProjectBuildingRequest expression to replace MavenProject argument.
     * This implements the generic transformation rule with multiple strategies.
     */
    private CtExpression<?> createProjectBuildingRequestExpression(
            CtExpression<?> mavenProjectExpr, CtInvocation<?> contextInvocation) {
        
        // Strategy 1: Look for existing ProjectBuildingRequest in surrounding context
        String existingPbr = findExistingProjectBuildingRequest(contextInvocation);
        if (existingPbr != null) {
            System.out.println("  Strategy 1: Using existing ProjectBuildingRequest: " + existingPbr);
            return factory.createCodeSnippetExpression(existingPbr);
        }
        
        // Strategy 2: Create from MavenSession if available
        String sessionPbr = createFromMavenSession(contextInvocation);
        if (sessionPbr != null) {
            System.out.println("  Strategy 2: Creating from MavenSession: " + sessionPbr);
            return factory.createCodeSnippetExpression(sessionPbr);
        }
        
        // Strategy 3: Default - create new DefaultProjectBuildingRequest()
        System.out.println("  Strategy 3: Creating new DefaultProjectBuildingRequest()");
        System.out.println("  Note: May need manual configuration (repositories, settings, etc.)");
        return factory.createCodeSnippetExpression("new org.apache.maven.project.DefaultProjectBuildingRequest()");
    }
    
    /**
     * Look for existing ProjectBuildingRequest in context.
     * Returns code snippet if found, null otherwise.
     */
    private String findExistingProjectBuildingRequest(CtInvocation<?> contextInvocation) {
        // Check surrounding class for ProjectBuildingRequest fields or methods
        CtType<?> surroundingType = contextInvocation.getParent(CtType.class);
        if (surroundingType == null) {
            return null;
        }
        
        // Look for getBuildingRequest() method
        for (CtMethod<?> method : surroundingType.getMethods()) {
            if ("getBuildingRequest".equals(method.getSimpleName())) {
                System.out.println("    Found getBuildingRequest() method");
                return "getBuildingRequest()";
            }
        }
        
        // Look for buildingRequest field
        for (CtField<?> field : surroundingType.getFields()) {
            String fieldName = field.getSimpleName();
            if (fieldName.toLowerCase().contains("buildingrequest") || 
                fieldName.toLowerCase().contains("request")) {
                System.out.println("    Found potential ProjectBuildingRequest field: " + fieldName);
                return fieldName;
            }
        }
        
        return null;
    }
    
    /**
     * Create ProjectBuildingRequest from MavenSession if available.
     */
    private String createFromMavenSession(CtInvocation<?> contextInvocation) {
        CtType<?> surroundingType = contextInvocation.getParent(CtType.class);
        if (surroundingType == null) {
            return null;
        }
        
        // Look for session field
        for (CtField<?> field : surroundingType.getFields()) {
            String fieldName = field.getSimpleName();
            if (fieldName.toLowerCase().contains("session")) {
                System.out.println("    Found MavenSession field: " + fieldName);
                return fieldName + ".getProjectBuildingRequest()";
            }
        }
        
        // Look for getSession() method
        for (CtMethod<?> method : surroundingType.getMethods()) {
            if ("getSession".equals(method.getSimpleName())) {
                System.out.println("    Found getSession() method");
                return "getSession().getProjectBuildingRequest()";
            }
        }
        
        return null;
    }
    
    /**
     * Create code for new invocation.
     */
    private String createNewInvocationCode(CtInvocation<?> oldInvocation,
                                           CtExpression<?> newFirstArg,
                                           CtExpression<?> secondArg) {
        // Get the target expression (e.g., "graph" in "graph.buildDependencyGraph(...)")
        CtExpression<?> target = oldInvocation.getTarget();
        String targetCode = target != null ? target.toString() : "";
        
        // Create the new invocation code
        return targetCode + "buildDependencyGraph(" + newFirstArg + ", " + secondArg + ")";
    }
    
    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java GenericMavenDependencyTreeFix <source-directory>");
            System.err.println("Example: java GenericMavenDependencyTreeFix /path/to/project/src/main/java");
            System.err.println("");
            System.err.println("This transformation fixes the maven-dependency-tree 3.1.0 API breaking change:");
            System.err.println("  OLD: buildDependencyGraph(MavenProject project, ArtifactFilter filter)");
            System.err.println("  NEW: buildDependencyGraph(ProjectBuildingRequest request, ArtifactFilter filter)");
            System.err.println("");
            System.err.println("The transformation applies these strategies:");
            System.err.println("  1. Use existing ProjectBuildingRequest (getBuildingRequest() method or field)");
            System.err.println("  2. Create from MavenSession (session.getProjectBuildingRequest())");
            System.err.println("  3. Create new DefaultProjectBuildingRequest() (may need manual configuration)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Maven Dependency Tree 3.1.0 API Fix Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            GenericMavenDependencyTreeFix transformer = new GenericMavenDependencyTreeFix(launcher.getFactory());
            transformer.transform(model);
            
            if (transformer.getTransformationCount() > 0) {
                // Output transformed code
                String outputDir = "./transformed-output";
                launcher.setSourceOutputDirectory(outputDir);
                launcher.prettyprint();
                
                System.out.println("\n=== Transformation Complete ===");
                System.out.println("Successfully transformed " + transformer.getTransformationCount() + " buildDependencyGraph() calls");
                System.out.println("Output written to: " + outputDir);
                System.out.println("");
                System.out.println("IMPORTANT: Review the transformed code:");
                System.out.println("1. Verify ProjectBuildingRequest is properly created/configured");
                System.out.println("2. Check if repositories, settings, or other properties need to be set");
                System.out.println("3. Test compilation and runtime behavior");
            } else {
                System.out.println("No buildDependencyGraph calls found to transform.");
                System.out.println("This could mean:");
                System.out.println("1. The project doesn't use maven-dependency-tree");
                System.out.println("2. The project already uses the new API (ProjectBuildingRequest)");
                System.out.println("3. The calls use different method names or patterns");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}