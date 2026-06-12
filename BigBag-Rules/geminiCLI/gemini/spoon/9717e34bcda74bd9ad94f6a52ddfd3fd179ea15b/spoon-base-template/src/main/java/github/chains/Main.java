package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        process("/workspace/jcabi-github/src/main/java");
        process("/workspace/jcabi-github/src/test/java");
    }

    private static void process(String path) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(path);
        launcher.setSourceOutputDirectory(path);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addProcessor(new TvReplacerProcessor());
        launcher.run();
    }
}