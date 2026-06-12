package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/docker-adapter/src/test/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory("/workspace/spoon-base-template/spooned");
        launcher.addProcessor(new HamcrestConstructorFixer());
        launcher.run();
    }
}
