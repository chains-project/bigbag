package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    private static Launcher launcher;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying tinspin-indexes 2.0.0 migration to: " + sourceDir);
        
        launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        applyTransformations(model);
        
        // Write transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed.");
    }
    
    private static void applyTransformations(CtModel model) {
        // Transform imports
        transformImports(model);
        
        // Transform type references
        transformTypeReferences(model);
        
        // Transform method calls and constructor calls
        transformMethodCalls(model);
        
        // Transform field declarations
        transformFieldDeclarations(model);
    }
    
    private static void transformImports(CtModel model) {
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            String importStr = ctImport.getReference().toString();
            
            // Replace PointIndex import with PointMap
            if (importStr.equals("org.tinspin.index.PointIndex")) {
                ctImport.setReference(launcher.getFactory().createReference("org.tinspin.index.PointMap"));
                System.out.println("Transformed import: PointIndex -> PointMap");
            }
            // Replace PointDistanceFunction import with PointDistance
            else if (importStr.equals("org.tinspin.index.PointDistanceFunction")) {
                ctImport.setReference(launcher.getFactory().createReference("org.tinspin.index.PointDistance"));
                System.out.println("Transformed import: PointDistanceFunction -> PointDistance");
            }
            // Replace PointEntryDist import with Index.PointEntryKnn
            else if (importStr.equals("org.tinspin.index.PointEntryDist")) {
                ctImport.setReference(launcher.getFactory().createReference("org.tinspin.index.Index.PointEntryKnn"));
                System.out.println("Transformed import: PointEntryDist -> Index.PointEntryKnn");
            }
        }
    }
    
    private static void transformTypeReferences(CtModel model) {
        // Transform type references in code (variable declarations, method parameters, etc.)
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            
            if (typeName.equals("org.tinspin.index.PointIndex")) {
                // Replace type reference
                CtTypeReference<?> newTypeRef = launcher.getFactory().createReference("org.tinspin.index.PointMap");
                typeRef.replace(newTypeRef);
                System.out.println("Transformed type reference: PointIndex -> PointMap");
            }
            else if (typeName.equals("org.tinspin.index.PointDistanceFunction")) {
                CtTypeReference<?> newTypeRef = launcher.getFactory().createReference("org.tinspin.index.PointDistance");
                typeRef.replace(newTypeRef);
                System.out.println("Transformed type reference: PointDistanceFunction -> PointDistance");
            }
            else if (typeName.equals("org.tinspin.index.PointEntryDist")) {
                CtTypeReference<?> newTypeRef = launcher.getFactory().createReference("org.tinspin.index.Index.PointEntryKnn");
                typeRef.replace(newTypeRef);
                System.out.println("Transformed type reference: PointEntryDist -> Index.PointEntryKnn");
            }
        }
    }
    
    private static void transformFieldDeclarations(CtModel model) {
        // Transform field type declarations
        List<CtField<?>> fields = model.getElements(new TypeFilter<>(CtField.class));
        for (CtField<?> field : fields) {
            CtTypeReference<?> fieldType = field.getType();
            if (fieldType != null) {
                String typeName = fieldType.getQualifiedName();
                if (typeName.equals("org.tinspin.index.PointDistanceFunction")) {
                    field.setType(launcher.getFactory().createReference("org.tinspin.index.PointDistance"));
                    System.out.println("Transformed field type: PointDistanceFunction -> PointDistance");
                }
            }
        }
    }
    
    private static void transformMethodCalls(CtModel model) {
        // Transform KDTree.create() calls with distance function parameter
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            // Check if this is KDTree.create() or CoverTree.create() invocation
            String methodName = invocation.getExecutable().getSimpleName();
            if (methodName.equals("create")) {
                // Check target type
                CtExpression<?> target = invocation.getTarget();
                if (target != null) {
                    String targetType = target.getType().getQualifiedName();
                    if (targetType.equals("org.tinspin.index.kdtree.KDTree")) {
                        // KDTree.create() - remove distance function parameter if present
                        if (invocation.getArguments().size() == 2) {
                            // Assume second argument is distance function lambda
                            invocation.getArguments().remove(1);
                            System.out.println("Removed distance function parameter from KDTree.create() call");
                        }
                    }
                }
            }
        }
        
        // Also check constructor calls
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> ctorCall : constructorCalls) {
            CtTypeReference<?> ctorType = ctorCall.getType();
            if (ctorType != null) {
                String typeName = ctorType.getQualifiedName();
                if (typeName.equals("org.tinspin.index.kdtree.KDTree")) {
                    // KDTree constructor call - similar to static create() method
                    if (ctorCall.getArguments().size() == 2) {
                        ctorCall.getArguments().remove(1);
                        System.out.println("Removed distance function parameter from KDTree constructor");
                    }
                }
            }
        }
    }
}