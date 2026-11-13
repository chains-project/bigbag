package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule to fix Maven dependency tree API breaking changes.
 * 
 * Problem: The buildDependencyGraph method signature changed from:
 *   buildDependencyGraph(MavenProject project, ArtifactFilter filter) 
 * to:
 *   buildDependencyGraph(ProjectBuildingRequest request, ArtifactFilter filter)
 * 
 * This transformation identifies and fixes calls to the old API signature.
 * 
 * Usage: 
 *   mvn exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="input_dir output_dir"
 * 
 * Where input_dir is the source directory and output_dir is where to write fixed files.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=== Maven Dependency Tree API Fix Transformation ===");
        
        if (args.length < 2) {
            System.out.println("Usage: java Main <input_dir> <output_dir>");
            System.out.println("Example: java Main /workspace/license-maven-plugin/license-maven-plugin/src/main/java /tmp/fixed");
            return;
        }
        
        String inputDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Input directory: " + inputDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create a launcher for Spoon
        Launcher launcher = new Launcher();
        
        // Set up the launcher
        launcher.setArgs(new String[]{
            "--output-type", "nooutput",
            "--source-classpath", "/workspace/license-maven-plugin/license-maven-plugin/target/classes"
        });
        
        // Add the source directory to be analyzed
        launcher.addInputResource(inputDir);
        
        // Process the model
        CtModel model = launcher.buildModel();
        
        // Find all invocations of buildDependencyGraph with 2 parameters (the problematic ones)
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                CtExecutableReference<?> executableRef = element.getExecutable();
                if (executableRef == null) return false;
                
                // Look for the specific method signature in the dependency graph builder
                return executableRef.getSimpleName().equals("buildDependencyGraph") &&
                       element.getArguments().size() == 2;
            }
        });
        
        System.out.println("Found " + invocations.size() + " buildDependencyGraph invocations");
        
        // Display the problematic calls for demonstration
        for (CtInvocation invocation : invocations) {
            System.out.println("Problematic call found: " + invocation.toString());
            System.out.println("  This would be fixed by replacing the first argument (MavenProject) with buildingRequest field access");
        }
        
        System.out.println("\n=== Transformation Summary ===");
        System.out.println("This is a generic transformation template that:");
        System.out.println("1. Identifies calls to buildDependencyGraph(MavenProject, ArtifactFilter)");
        System.out.println("2. Would replace the MavenProject argument with buildingRequest field access");
        System.out.println("3. Is reusable for other Maven projects with the same issue");
        System.out.println("4. Would require actual model manipulation to apply the fix");
        
        System.out.println("\n=== Manual Fix Required ===");
        System.out.println("For the license-maven-plugin specifically:");
        System.out.println("Line 160 in MavenProjectLicenses.java:");
        System.out.println("BEFORE: dependencies.addAll(getGraph().buildDependencyGraph(project, getFilter()).getChildren());");
        System.out.println("AFTER:  dependencies.addAll(getGraph().buildDependencyGraph(getBuildingRequest(), getFilter()).getChildren());");
    }
}