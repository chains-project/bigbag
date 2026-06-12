package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/singer/singer/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/spoon-base-template/target/spooned");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addProcessor(new TFramedTransportTransformer());
        launcher.addProcessor(new CompilationFixerTransformer());
        launcher.run();
    }
}
