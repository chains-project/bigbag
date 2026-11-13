package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.Filter;
import spoon.reflect.code.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        System.out.println("Processing source directory: " + sourcePath);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourcePath);
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            int transformedElements = 0;
            Set<String> transformedTypes = new HashSet<>();
            
            // 1. Transform imports
            List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
            for (CtImport ctImport : imports) {
                CtReference importRef = ctImport.getReference();
                if (importRef != null) {
                    String importStr = importRef.toString();
                    if (importStr.startsWith("javax.validation")) {
                        String newImportStr = importStr.replace("javax.validation", "jakarta.validation");
                        
                        if (importRef instanceof CtTypeReference) {
                            CtTypeReference typeRef = (CtTypeReference) importRef;
                            String qualifiedName = typeRef.getQualifiedName();
                            String newQualifiedName = qualifiedName.replace("javax.validation", "jakarta.validation");
                            
                            // Create new type reference
                            CtTypeReference newTypeRef = launcher.getFactory().createReference(newQualifiedName);
                            ctImport.setReference(newTypeRef);
                            
                            transformedElements++;
                            transformedTypes.add(qualifiedName);
                        } else if (importRef instanceof CtPackageReference) {
                            CtPackageReference pkgRef = (CtPackageReference) importRef;
                            String pkgName = pkgRef.getSimpleName();
                            String newPkgName = pkgName.replace("javax.validation", "jakarta.validation");
                            
                            pkgRef.setSimpleName(newPkgName);
                            transformedElements++;
                            transformedTypes.add(pkgName);
                        }
                    }
                }
            }
            
            // 2. Transform type references in the code (fields, parameters, return types, etc.)
            List<CtTypeReference> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith("javax.validation")) {
                    String newQualifiedName = qualifiedName.replace("javax.validation", "jakarta.validation");
                    
                    // Create new type reference
                    CtTypeReference newTypeRef = launcher.getFactory().createReference(newQualifiedName);
                    
                    // Replace in parent element
                    CtElement parent = typeRef.getParent();
                    if (parent instanceof CtVariable) {
                        ((CtVariable) parent).setType(newTypeRef);
                    } else if (parent instanceof CtParameter) {
                        ((CtParameter) parent).setType(newTypeRef);
                    } else if (parent instanceof CtMethod) {
                        ((CtMethod) parent).setType(newTypeRef);
                    } else if (parent instanceof CtField) {
                        ((CtField) parent).setType(newTypeRef);
                    } else {
                        // Generic replacement - need to check if parent can accept replacement
                        typeRef.replace(newTypeRef);
                    }
                    
                    transformedElements++;
                    transformedTypes.add(qualifiedName);
                }
            }
            
            // 3. Transform constructor and method calls that use validation types
            List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
            for (CtConstructorCall call : constructorCalls) {
                CtTypeReference typeRef = call.getType();
                if (typeRef != null) {
                    String qualifiedName = typeRef.getQualifiedName();
                    if (qualifiedName != null && qualifiedName.startsWith("javax.validation")) {
                        String newQualifiedName = qualifiedName.replace("javax.validation", "jakarta.validation");
                        CtTypeReference newTypeRef = launcher.getFactory().createReference(newQualifiedName);
                        call.setType(newTypeRef);
                        transformedElements++;
                        transformedTypes.add(qualifiedName);
                    }
                }
            }
            
            System.out.println("Transformed " + transformedElements + " elements");
            System.out.println("Affected types: " + String.join(", ", transformedTypes));
            
            // Write transformed code back
            launcher.setSourceOutputDirectory(sourcePath);
            launcher.prettyprint();
            
            System.out.println("Transformation complete! Applied javax.validation -> jakarta.validation migration");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}