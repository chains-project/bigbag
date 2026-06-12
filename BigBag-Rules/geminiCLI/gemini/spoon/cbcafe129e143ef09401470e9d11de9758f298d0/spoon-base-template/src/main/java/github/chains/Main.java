package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Point ONLY to the file we want to fix
        launcher.addInputResource("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new LogbackFixProcessor());
        
        launcher.setSourceOutputDirectory("/workspace/spoon-base-template/spooned");
        
        launcher.run();
    }
}