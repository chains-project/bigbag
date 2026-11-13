package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtPackage;

public class Main {
    public static void main(String[] args) {
        // Create a spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source code directory (this would be the PGS project)
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        
        // Add the processors
        launcher.addProcessor(new PointIndexKNNFixerProcessor());
        
        // Process the code (in a real scenario, we'd point it to the PGS project)
        System.out.println("Spoon transformation initialized for fixing tinspin API breaking changes");
        System.out.println("This would process the PGS project and fix queryKNN calls");
    }
}