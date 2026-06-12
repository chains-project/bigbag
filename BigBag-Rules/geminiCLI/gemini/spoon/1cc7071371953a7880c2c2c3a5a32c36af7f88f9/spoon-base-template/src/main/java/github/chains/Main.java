package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/assertj-vavr/src/main/java/org/assertj/vavr/api/VavrAssumptions.java");
        launcher.addInputResource("/workspace/assertj-vavr/src/main/java/org/assertj/vavr/api/ClassLoadingStrategyFactory.java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new FixByteBuddyImports());
        launcher.setSourceOutputDirectory("/workspace/assertj-vavr/src/main/java");
        
        launcher.run();
        
        System.out.println("Transformation complete.");
    }
}
