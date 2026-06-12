package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/quickfixj/quickfixj-core/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/quickfixj/quickfixj-core/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new MinaMigrationProcessor());
        launcher.run();
        
        System.out.println("Mina migration applied.");
    }
}