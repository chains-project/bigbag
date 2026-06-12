package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Input: the source code of the project to fix
        launcher.addInputResource("/workspace/incrementals-tools/lib/src/main/java");
        // Output: where to save the transformed code
        launcher.setSourceOutputDirectory("/workspace/spooned-output");
        
        launcher.addProcessor(new FixGHCompareStatusAccessProcessor());
        
        // Important for processing code without full classpath
        launcher.getEnvironment().setNoClasspath(true);
        // Preserve comments and formatting as much as possible
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setAutoImports(true);
        
        launcher.run();
    }
}
