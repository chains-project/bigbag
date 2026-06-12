package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addProcessor(new FixSonarLintEngineProcessor());
        launcher.setSourceOutputDirectory("/workspace/spoon-base-template/spooned");
        launcher.run();
    }
}
