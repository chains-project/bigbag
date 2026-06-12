package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        
        launcher.addInputResource("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java");
        launcher.addInputResource("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineGeneralTest.java");
        launcher.addInputResource("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/BatchUpdateTest.java");
        
        launcher.setSourceOutputDirectory("/workspace/pdb/src/test/java");
        
        launcher.addProcessor(new LogbackFixProcessor());
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.run();
        System.out.println("Spoon transformation complete.");
    }
}
