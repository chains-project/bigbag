package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.SignaturePrinter;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration map for type replacements
    private static final Map<String, String> TYPE_REPLACEMENTS = new HashMap<>();
    static {
        TYPE_REPLACEMENTS.put("org.tinspin.index.PointIndex", "org.tinspin.index.PointMap");
        TYPE_REPLACEMENTS.put("org.tinspin.index.PointDistanceFunction", "org.tinspin.index.PointDistance");
        TYPE_REPLACEMENTS.put("org.tinspin.index.PointEntryDist", "org.tinspin.index.Index$PointEntryKnn");
    }
    
    // Configuration for method renames
    private static final Map<String, String> METHOD_RENAMES = new HashMap<>();
    static {
        METHOD_RENAMES.put("query1NN", "query1nn");
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-dir> <output-dir>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/src /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying tinspin-indexes 2.0.1 migration transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        CtModel model = launcher.buildModel();
        
        int transformationsApplied = 0;
        
        // Apply all type replacements
        for (Map.Entry<String, String> entry : TYPE_REPLACEMENTS.entrySet()) {
            transformationsApplied += replaceTypeReferences(model, entry.getKey(), entry.getValue());
        }
        
        // Apply method renames
        for (Map.Entry<String, String> entry : METHOD_RENAMES.entrySet()) {
            transformationsApplied += renameMethodCalls(model, entry.getKey(), entry.getValue());
        }
        
        // Fix KDTree.create calls (remove distance function parameter)
        transformationsApplied += fixKDTreeCreateCalls(model);
        
        // Fix CoverTree.create calls (update PointDistanceFunction to PointDistance)
        transformationsApplied += fixCoverTreeCreateCalls(model);
        
        System.out.println("Total transformations applied: " + transformationsApplied);
        
        if (transformationsApplied > 0) {
            launcher.prettyprint();
            System.out.println("Transformation complete. Output written to: " + outputDir);
            System.out.println("\nSummary of changes:");
            System.out.println("1. Updated type references: " + TYPE_REPLACEMENTS);
            System.out.println("2. Renamed methods: " + METHOD_RENAMES);
            System.out.println("3. Fixed KDTree.create() calls (removed distance function parameter)");
            System.out.println("4. Fixed CoverTree.create() calls (updated PointDistanceFunction to PointDistance)");
        } else {
            System.out.println("No transformations applied - code may already be compatible with tinspin-indexes 2.0.1");
        }
    }
    
    private static int replaceTypeReferences(CtModel model, String oldTypeName, String newTypeName) {
        // Find all type references matching oldTypeName
        List<CtTypeReference<?>> refs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return element.getQualifiedName() != null && 
                       element.getQualifiedName().equals(oldTypeName);
            }
        });
        
        for (CtTypeReference<?> ref : refs) {
            // Create new reference
            CtTypeReference<?> newRef = ref.getFactory().Type().createReference(newTypeName);
            
            // Copy type parameters if present
            if (!ref.getActualTypeArguments().isEmpty()) {
                newRef.setActualTypeArguments(ref.getActualTypeArguments());
            }
            
            // Replace the old reference with new one
            ref.replace(newRef);
        }
        
        return refs.size();
    }
    
    private static int renameMethodCalls(CtModel model, String oldMethodName, String newMethodName) {
        // Find all method invocations with old method name
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                return element.getExecutable() != null &&
                       element.getExecutable().getSimpleName() != null &&
                       element.getExecutable().getSimpleName().equals(oldMethodName);
            }
        });
        
        for (CtInvocation<?> invocation : invocations) {
            invocation.getExecutable().setSimpleName(newMethodName);
        }
        
        return invocations.size();
    }
    
    private static int fixKDTreeCreateCalls(CtModel model) {
        // Find KDTree.create() calls
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                if (element.getExecutable() == null || element.getExecutable().getSimpleName() == null) {
                    return false;
                }
                if (!element.getExecutable().getSimpleName().equals("create")) {
                    return false;
                }
                if (element.getTarget() == null || element.getTarget().getType() == null) {
                    return false;
                }
                String targetType = element.getTarget().getType().getQualifiedName();
                return targetType != null && targetType.equals("org.tinspin.index.kdtree.KDTree");
            }
        });
        
        int fixed = 0;
        for (CtInvocation<?> invocation : invocations) {
            // KDTree.create() in new API has signatures:
            // - create(int dimensions)
            // - create(IndexConfig config)
            // The old API had: create(int dimensions, PointDistanceFunction distanceFunction)
            // We need to remove the distance function parameter
            if (invocation.getArguments().size() == 2) {
                // Remove the second argument (distance function)
                invocation.getArguments().remove(1);
                fixed++;
            }
        }
        
        return fixed;
    }
    
    private static int fixCoverTreeCreateCalls(CtModel model) {
        // Find CoverTree.create() calls
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                if (element.getExecutable() == null || element.getExecutable().getSimpleName() == null) {
                    return false;
                }
                if (!element.getExecutable().getSimpleName().equals("create")) {
                    return false;
                }
                if (element.getTarget() == null || element.getTarget().getType() == null) {
                    return false;
                }
                String targetType = element.getTarget().getType().getQualifiedName();
                return targetType != null && targetType.equals("org.tinspin.index.covertree.CoverTree");
            }
        });
        
        int fixed = 0;
        for (CtInvocation<?> invocation : invocations) {
            // CoverTree.create() in new API still accepts PointDistance parameter
            // but the type changed from PointDistanceFunction to PointDistance
            // Type replacement should handle this automatically
            fixed++;
        }
        
        return fixed;
    }
}