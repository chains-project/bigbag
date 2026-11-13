import spoon.Launcher;

public class TestSpoon {
    public static void main(String[] args) {
        System.out.println("TestSpoon starting...");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/polyglot-maven/polyglot-yaml/src/main/java/org/sonatype/maven/polyglot/yaml/ModelConstructor.java");
        launcher.setSourceOutputDirectory("/tmp/spoon-test");
        
        System.out.println("Building model...");
        launcher.buildModel();
        System.out.println("Model built successfully!");
        
        System.out.println("Number of types: " + launcher.getFactory().Type().getAll().size());
    }
}