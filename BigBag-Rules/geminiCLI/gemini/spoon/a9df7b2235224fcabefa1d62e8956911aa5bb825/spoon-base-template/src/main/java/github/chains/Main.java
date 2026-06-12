package github.chains;

import spoon.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/polyglot-maven/polyglot-yaml/src/main/java/org/sonatype/maven/polyglot/yaml/ModelRepresenter.java");
        launcher.setSourceOutputDirectory("/workspace/polyglot-maven/polyglot-yaml/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addProcessor(new RemoveIntrospectionExceptionProcessor());
        launcher.run();
        System.out.println("Spoon processing complete.");
    }
}