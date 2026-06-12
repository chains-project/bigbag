package github.chains;

import spoon.Launcher;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        launcher.setSourceOutputDirectory("/workspace/pay-adminusers/src/test/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addProcessor(new FixLoggingDependencyProcessor());
        launcher.run();
        
        System.out.println("Spoon transformation finished.");
    }
}
