package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Input: Only the file we want to change
        launcher.addInputResource("/workspace/license-maven-plugin/license-maven-plugin/src/main/java/com/mycila/maven/plugin/license/dependencies/MavenProjectLicenses.java");
        
        // Output: A temporary directory inside spoon-base-template
        launcher.setSourceOutputDirectory("/workspace/spoon-base-template/target/spooned");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true); // Try to preserve comments
        
        launcher.addProcessor(new FixDependencyGraphBuilderProcessor());
        
        launcher.run();
        
        System.out.println("Spoon transformation finished.");
    }
}
