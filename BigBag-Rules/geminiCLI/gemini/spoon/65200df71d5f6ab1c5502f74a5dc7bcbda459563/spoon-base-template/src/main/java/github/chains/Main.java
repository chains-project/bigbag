package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Set input resource
        launcher.addInputResource("/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java");
        
        // Use NOCLASSPATH mode as we don't have all dependencies available for resolution
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        // Preserve comments and formatting as much as possible
        launcher.getEnvironment().setCommentEnabled(true);

        // Build model
        launcher.buildModel();
        CtModel model = launcher.getModel();

        model.getAllTypes().forEach(t -> System.out.println("Found type: " + t.getQualifiedName()));

        // Find the class
        CtType<?> type = model.getAllTypes().stream()
            .filter(t -> t.getSimpleName().equals("SonarLintEngine"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Class not found"));

        // Find the method buildAnalysisEngineConfiguration
        CtMethod<?> method = type.getMethodsByName("buildAnalysisEngineConfiguration").stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Method not found"));

        // Find the invocation to remove: .addEnabledLanguages(...)
        CtInvocation<?> invocationToRemove = method.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))
            .stream()
            .filter(inv -> inv.getExecutable().getSimpleName().equals("addEnabledLanguages"))
            .findFirst()
            .orElse(null);

        if (invocationToRemove != null) {
            System.out.println("Found addEnabledLanguages invocation. Removing it...");
            
            // Check if it is part of a fluent chain (which it is)
            if (invocationToRemove.getParent() instanceof CtInvocation) {
                CtInvocation<?> parent = (CtInvocation<?>) invocationToRemove.getParent();
                // We replace the target of the parent call with the target of the removed call
                // effectively splicing it out of the chain.
                // Parent call is likely setClientPid(...) or similar.
                // Target of removed call is AnalysisEngineConfiguration.builder() or previous call.
                parent.setTarget(invocationToRemove.getTarget());
                System.out.println("Removed addEnabledLanguages call from the chain.");
            } else {
                 System.out.println("Unexpected structure: parent is not an invocation. Parent type: " + invocationToRemove.getParent().getClass().getSimpleName());
            }
        } else {
             System.out.println("addEnabledLanguages call not found. Maybe it's already removed?");
        }

        // Output the modified file
        // We will read the pretty printed content and write it to the file manually
        // because we want to overwrite the original file in place and Spoon's output directory logic
        // can be a bit verbose to configure for a single file overwrite in a different project.
        
        spoon.reflect.declaration.CtCompilationUnit cu = type.getFactory().CompilationUnit().getOrCreate(type);
        String content = launcher.getEnvironment().createPrettyPrinter().printCompilationUnit(cu);
        
        System.out.println("Content length: " + content.length());
        if (content.length() < 100) {
             System.out.println("Content: " + content);
        }
        
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java"), 
                content.getBytes()
            );
            System.out.println("File saved.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
