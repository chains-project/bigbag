package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) {
        // Simple replacement approach to fix Maven dependency issue
        String sourceDir = "/workspace/license-maven-plugin/license-maven-plugin/src/main/java";
        if (args.length > 0) {
            sourceDir = args[0];
        }
        
        // Fix imports in the specific file
        fixFile(sourceDir + "/com/mycila/maven/plugin/license/dependencies/MavenProjectLicenses.java");
        
        System.out.println("Transformation completed.");
    }
    
    private static void fixFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String content = new String(Files.readAllBytes(path));
            
            // Replace import statement
            content = content.replaceAll(
                "import org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder;",
                "import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder;"
            );
            
            // Replace instantiation
            content = content.replaceAll(
                "new Maven31DependencyGraphBuilder\\(\\)",
                "new DefaultDependencyGraphBuilder()"
            );
            
            Files.write(path, content.getBytes());
            System.out.println("Fixed file: " + filePath);
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}
        
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory("/tmp/spoon-generated");
        
        // Add the Spoon Java processor
        launcher.addProcessor(new DependencyGraphBuilderFixProcessor());
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Process the model
        launcher.process();
        
        // Print the result
        System.out.println("Transformation completed.");
    }
}