package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/gauge-java/src/main/java");
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addProcessor(new ReflectionsFixProcessor());
        launcher.setSourceOutputDirectory("/workspace/gauge-java/src/main/java");
        launcher.run();
    }
}
