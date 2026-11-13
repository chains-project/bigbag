package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import java.util.*;
import java.util.function.Consumer;

public class Main {
    
    // OLD API patterns (from tinspin-indexes 1.x)
    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    
    // NEW API patterns (tinspin-indexes 2.0.1)
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";
    private static final String NEW_POINT_MULTIMAP = "org.tinspin.index.PointMultimap";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index$PointEntryKnn";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-dir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying tinspin-indexes 2.0.1 migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        // We need to disable classpath checking since we're modifying code that
        // references old API that doesn't exist anymore
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setLevel("OFF"); // Reduce verbosity
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // 1. Transform imports and type references
            transformTypeReferences(model);
            
            // 2. Transform method calls: query1NN -> query1nn
            transformMethodCalls(model);
            
            // 3. Transform KDTree.create() calls with distance functions
            transformKDTreeCreations(model);
            
            // 4. Write transformed code back
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformTypeReferences(CtModel model) {
        // Transform import statements
        for (CtType<?> type : model.getAllTypes()) {
            // Process imports
            for (CtImport ctImport : type.getPosition().getCompilationUnit().getImports()) {
                if (ctImport.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> importRef = (CtTypeReference<?>) ctImport.getReference();
                    String importQualifiedName = importRef.getQualifiedName();
                    
                    if (OLD_POINT_INDEX.equals(importQualifiedName)) {
                        // Replace PointIndex import with PointMap import
                        importRef.setSimpleName(NEW_POINT_MAP.substring(NEW_POINT_MAP.lastIndexOf('.') + 1));
                        CtPackageReference pkgRef = importRef.getFactory().createPackageReference();
                        pkgRef.setSimpleName(NEW_POINT_MAP.substring(0, NEW_POINT_MAP.lastIndexOf('.')));
                        importRef.setPackage(pkgRef);
                    } else if (OLD_POINT_DISTANCE_FUNCTION.equals(importQualifiedName)) {
                        // Replace PointDistanceFunction import with PointDistance import
                        importRef.setSimpleName(NEW_POINT_DISTANCE.substring(NEW_POINT_DISTANCE.lastIndexOf('.') + 1));
                        CtPackageReference pkgRef = importRef.getFactory().createPackageReference();
                        pkgRef.setSimpleName(NEW_POINT_DISTANCE.substring(0, NEW_POINT_DISTANCE.lastIndexOf('.')));
                        importRef.setPackage(pkgRef);
                    } else if (OLD_POINT_ENTRY_DIST.equals(importQualifiedName)) {
                        // Replace PointEntryDist import with Index.PointEntryKnn import
                        importRef.setSimpleName("PointEntryKnn");
                        CtPackageReference pkgRef = importRef.getFactory().createPackageReference();
                        pkgRef.setSimpleName("org.tinspin.index");
                        importRef.setPackage(pkgRef);
                    }
                }
            }
            
            // Transform type references in the code
            transformTypeReferencesInType(type);
        }
    }
    
    private static void transformTypeReferencesInType(CtType<?> type) {
        // Find all type references in this type
        List<CtTypeReference<?>> typeRefs = Query.getElements(type, new Filter<CtTypeReference<?>>() {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                String qualifiedName = element.getQualifiedName();
                return OLD_POINT_INDEX.equals(qualifiedName) ||
                       OLD_POINT_DISTANCE_FUNCTION.equals(qualifiedName) ||
                       OLD_POINT_ENTRY_DIST.equals(qualifiedName);
            }
        });
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            
            if (OLD_POINT_INDEX.equals(qualifiedName)) {
                // PointIndex<T> -> PointMap<T>
                typeRef.setSimpleName(NEW_POINT_MAP.substring(NEW_POINT_MAP.lastIndexOf('.') + 1));
                CtPackageReference pkgRef = typeRef.getFactory().createPackageReference();
                pkgRef.setSimpleName(NEW_POINT_MAP.substring(0, NEW_POINT_MAP.lastIndexOf('.')));
                typeRef.setPackage(pkgRef);
            } else if (OLD_POINT_DISTANCE_FUNCTION.equals(qualifiedName)) {
                // PointDistanceFunction -> PointDistance
                typeRef.setSimpleName(NEW_POINT_DISTANCE.substring(NEW_POINT_DISTANCE.lastIndexOf('.') + 1));
                CtPackageReference pkgRef = typeRef.getFactory().createPackageReference();
                pkgRef.setSimpleName(NEW_POINT_DISTANCE.substring(0, NEW_POINT_DISTANCE.lastIndexOf('.')));
                typeRef.setPackage(pkgRef);
            } else if (OLD_POINT_ENTRY_DIST.equals(qualifiedName)) {
                // PointEntryDist<T> -> Index$PointEntryKnn<T>
                typeRef.setSimpleName("PointEntryKnn");
                CtPackageReference pkgRef = typeRef.getFactory().createPackageReference();
                pkgRef.setSimpleName("org.tinspin.index");
                typeRef.setPackage(pkgRef);
            }
        }
    }
    
    private static void transformMethodCalls(CtModel model) {
        // Transform query1NN() calls to query1nn()
        List<CtInvocation<?>> invocations = model.getElements(new Filter<CtInvocation<?>>() {
            @Override
            public boolean matches(CtInvocation<?> element) {
                return "query1NN".equals(element.getExecutable().getSimpleName());
            }
        });
        
        for (CtInvocation<?> invocation : invocations) {
            // Change method name from query1NN to query1nn
            invocation.getExecutable().setSimpleName("query1nn");
        }
    }
    
    private static void transformKDTreeCreations(CtModel model) {
        // Find KDTree.create() calls with distance function parameter
        List<CtInvocation<?>> kdTreeCreations = model.getElements(new Filter<CtInvocation<?>>() {
            @Override
            public boolean matches(CtInvocation<?> element) {
                if (!"create".equals(element.getExecutable().getSimpleName())) {
                    return false;
                }
                
                CtTypeReference<?> targetType = element.getExecutable().getDeclaringType();
                if (targetType == null) {
                    return false;
                }
                
                // Check if it's KDTree.create() or similar factory method
                String typeName = targetType.getQualifiedName();
                return typeName != null && 
                       (typeName.contains("KDTree") || 
                        typeName.contains("CoverTree") ||
                        typeName.contains("QuadTree"));
            }
        });
        
        for (CtInvocation<?> creation : kdTreeCreations) {
            // Check if this is a create() call with more than 1 argument
            // (which would be the old style: create(dims, distanceFunction))
            if (creation.getArguments().size() >= 2) {
                CtExpression<?> secondArg = creation.getArguments().get(1);
                
                // Get the factory for creating code elements
                spoon.reflect.factory.Factory factory = creation.getFactory();
                
                // Create a comment warning about the removed distance function
                CtComment comment = factory.createInlineComment(
                    "WARNING: Distance function removed. In tinspin-indexes 2.0+, " +
                    "distance functions are passed to queryKnn() method calls, " +
                    "not to tree constructors."
                );
                
                // Add the comment before the creation call
                if (creation.getParent() instanceof CtStatement) {
                    CtStatement parentStmt = (CtStatement) creation.getParent();
                    parentStmt.addComment(comment);
                }
                
                // Remove the distance function argument (the lambda)
                creation.getArguments().remove(1);
            }
        }
    }
}