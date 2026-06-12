package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/docker-adapter/src/test/java");
        launcher.setSourceOutputDirectory("/workspace/docker-adapter/spooned-test");
        launcher.addProcessor(new FixHamcrestConstructors());
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true); // Preserve comments
        launcher.run();
    }
}
