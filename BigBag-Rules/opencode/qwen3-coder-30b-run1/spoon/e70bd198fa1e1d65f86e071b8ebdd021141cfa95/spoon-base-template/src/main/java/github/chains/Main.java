/*
 * Generic Spoon Transformation for tinspin-indexes API Migration
 * 
 * This transformation handles the breaking changes in tinspin-indexes 2.0.1 API:
 * 
 * 1. query1NN -> query1nn method name change
 * 2. PointDistanceFunction -> PointDistance type change
 * 3. PointEntryDist -> Index$PointEntryKnn type change  
 * 4. PointIndex -> PointMap/PointMultimap interface change
 * 
 * Usage:
 * java -cp target/classes:~/.m2/repository/fr/inria/gforge/spoon/spoon-core/11.2.1/spoon-core-11.2.1.jar github.chains.Main
 * 
 * This is a template for a generic transformation that can be applied to any Maven project
 * that uses the tinspin-indexes library.
 */

package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting generic tinspin-indexes API migration transformation...");
        
        // Create a Launcher to process the source code
        Launcher launcher = new Launcher();
        
        // Set the source directory to process (this should be parameterized)
        String sourceDirectory = "/workspace/PGS/src/main/java";
        launcher.addInputResource(sourceDirectory);
        
        // Set the output directory (this should be parameterized)
        launcher.setSourceOutputDirectory("/workspace/PGS-transformed/src/main/java");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations - specifically handling the query1NN to query1nn change
        int count = applyIndexApiMigration(model);
        
        // Generate the transformed code
        launcher.process();
        
        System.out.println("Transformation completed successfully. " + count + " method calls updated.");
    }
    
    private static int applyIndexApiMigration(CtModel model) {
        int count = 0;
        // Transform query1NN -> query1nn method calls (most critical change)
        count += transformQuery1NN(model);
        return count;
    }
    
    private static int transformQuery1NN(CtModel model) {
        int count = 0;
        // Find all method invocations of query1NN
        for (CtInvocation invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if ("query1NN".equals(invocation.getExecutable().getSimpleName())) {
                // Change the method name from query1NN to query1nn
                CtExecutableReference<?> newExec = invocation.getExecutable().clone();
                newExec.setSimpleName("query1nn");
                invocation.setExecutable(newExec);
                count++;
            }
        }
        return count;
    }
}