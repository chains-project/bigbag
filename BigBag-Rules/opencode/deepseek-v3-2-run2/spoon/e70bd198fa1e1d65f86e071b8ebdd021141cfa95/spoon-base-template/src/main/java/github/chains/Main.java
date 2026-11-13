package github.chains;

import spoon.Launcher;
import spoon.compiler.SpoonResource;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.VirtualFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Main class to run the tinspin-indexes migration transformation.
 * 
 * This is a generic transformation that can be applied to any Java project
 * affected by the tinspin-indexes 2.0.1 breaking API changes.
 * 
 * Usage: java Main /path/to/source/code
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [output-directory]");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        String outputPath = args.length > 1 ? args[1] : sourcePath + "-transformed";
        
        System.out.println("Transforming tinspin-indexes API usage in: " + sourcePath);
        System.out.println("Output directory: " + outputPath);
        
        try {
            // Create launcher
            Launcher launcher = new Launcher();
            
            // Configure launcher
            launcher.addInputResource(sourcePath);
            launcher.setSourceOutputDirectory(outputPath);
            
            // Add processor
            launcher.addProcessor(new TinspinIndexesMigrationProcessor());
            
            // Build model
            System.out.println("Building Spoon model...");
            CtModel model = launcher.buildModel();
            
            // Process model
            System.out.println("Processing model with transformation rules...");
            launcher.process();
            
            // Write transformed code
            System.out.println("Writing transformed code to: " + outputPath);
            launcher.prettyprint();
            
            System.out.println("Transformation complete!");
            
            // Print summary
            List<CtType<?>> types = model.getAllTypes().stream().toList();
            System.out.println("Processed " + types.size() + " type(s)");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Helper method to demonstrate the transformation on a sample code snippet.
     */
    private static void runOnSampleCode() {
        String sampleCode = 
            "import org.tinspin.index.PointIndex;\n" +
            "import org.tinspin.index.PointDistanceFunction;\n" +
            "import org.tinspin.index.PointEntryDist;\n" +
            "import org.tinspin.index.kdtree.KDTree;\n" +
            "\n" +
            "public class Sample {\n" +
            "    public void oldMethod() {\n" +
            "        PointIndex<String> tree = KDTree.create(2, (p1, p2) -> {\n" +
            "            return Math.sqrt(Math.pow(p1[0] - p2[0], 2) + Math.pow(p1[1] - p2[1], 2));\n" +
            "        });\n" +
            "        PointEntryDist<String> nearest = tree.query1NN(new double[]{0, 0});\n" +
            "        System.out.println(nearest.value());\n" +
            "    }\n" +
            "}\n";
        
        try {
            Launcher launcher = new Launcher();
            SpoonResource resource = new VirtualFile(sampleCode, "Sample.java");
            launcher.addInputResource(resource);
            launcher.setSourceOutputDirectory("/tmp/spoon-output");
            launcher.addProcessor(new TinspinIndexesMigrationProcessor());
            
            launcher.buildModel();
            launcher.process();
            launcher.prettyprint();
            
            System.out.println("Sample transformation completed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}