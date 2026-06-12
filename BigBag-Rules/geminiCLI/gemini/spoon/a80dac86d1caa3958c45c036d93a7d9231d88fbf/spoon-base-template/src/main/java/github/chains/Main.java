package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Input file
        launcher.addInputResource("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        
        // Output directory - Spoon will append package structure to this
        launcher.setSourceOutputDirectory("/workspace/spooned");
        
        launcher.addProcessor(new LogbackFixProcessor());
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        
        launcher.run();
    }
}
