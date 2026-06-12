package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/singer/thrift-logger/src/main/java/com/pinterest/singer/client/logback/AppenderUtils.java");
        launcher.setSourceOutputDirectory("/workspace/singer/thrift-logger/src/main/java");
        launcher.addProcessor(new TransportFixProcessor());
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.run();
    }
}
