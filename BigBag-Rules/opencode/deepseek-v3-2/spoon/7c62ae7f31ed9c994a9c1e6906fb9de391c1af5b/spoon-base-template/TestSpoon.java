import spoon.Launcher;
import spoon.reflect.CtModel;

public class TestSpoon {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/onebusaway-gtfs-modules/onebusaway-gtfs-hibernate/src/main/java/org/onebusaway/gtfs/impl/ServiceDateUserType.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Apply transformation
        launcher.setSourceOutputDirectory("/workspace/onebusaway-gtfs-modules");
        launcher.prettyprint();
        
        System.out.println("Done");
    }
}
