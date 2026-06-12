package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/guice/extensions/struts2/test/com/google/inject/struts2/Struts2FactoryTest.java");
        launcher.setSourceOutputDirectory("/workspace/spoon-output");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new Struts2MigrationProcessor());
        
        launcher.run();
    }
}
