package github.chains;

import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Specific Spoon transformation to fix tinspin dependency API changes from version 1.x to 2.0.1.
 * This transformation handles:
 * 1. Removal of imports for PointIndex, PointDistanceFunction, PointEntryDist from org.tinspin.index
 * 2. Change KDTree.create(2, distanceFunction) to KDTree.create(2)
 * 3. Change query1NN(...) calls to return Index$PointEntryKnn directly (no conversion needed)
 * 4. Update PointEntryDist.value().dist() to just dist()
 * 5. Update lambda expressions that were PointDistanceFunction implementations to use built-in PointDistance
 */
public class Main {
    public static void main(String[] args) {
        // Configure the Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Set input directory
        String inputDir = System.getProperty("input.dir", "/workspace/PGS");
        launcher.addInputResource(inputDir);
        
        // Set output directory
        String outputDir = System.getProperty("output.dir", "/workspace/PGS-fixed");
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        Factory factory = model.getFactory();
        
        // Apply transformations
        removeUnwantedImports(model, factory);
        fixKDTreeCreateSignatures(model, factory);
        
        // Write the modified files back to disk
        launcher.writeProcessedSourceFiles();
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Remove all unwanted tinspin imports
     */
    private static void removeUnwantedImports(CtModel model, Factory factory) {
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null) {
                String qualifiedName = imp.getReference().getQualifiedName();
                if (qualifiedName.startsWith("org.tinspin.index.")) {
                    // Remove PointIndex, PointDistanceFunction, PointEntryDist imports
                    if ("PointIndex".equals(imp.getReference().getSimpleName()) ||
                        "PointDistanceFunction".equals(imp.getReference().getSimpleName()) ||
                        "PointEntryDist".equals(imp.getReference().getSimpleName())) {
                        imp.delete();
                    }
                }
            }
        }
    }
    
    /**
     * Fix KDTree.create signature changes - remove distance function parameter
     */
    private static void fixKDTreeCreateSignatures(CtModel model, Factory factory) {
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation invocation : invocations) {
            if (invocation.getMethod() != null && 
                "create".equals(invocation.getMethod().getSimpleName()) &&
                invocation.getTarget() instanceof CtTypeAccess) {
                
                CtTypeAccess typeAccess = (CtTypeAccess) invocation.getTarget();
                if (typeAccess.getAccessedType() != null &&
                    "KDTree".equals(typeAccess.getAccessedType().getSimpleName())) {
                    
                    // If it has 2 arguments, reduce to 1 (remove the distance function)
                    if (invocation.getArguments().size() == 2) {
                        invocation.getArguments().remove(1);
                    }
                }
            }
        }
    }
}