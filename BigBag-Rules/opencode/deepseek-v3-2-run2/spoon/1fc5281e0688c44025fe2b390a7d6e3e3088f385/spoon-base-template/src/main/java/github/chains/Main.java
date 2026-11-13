package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Main {
    
    private static final String JAVAX_SERVLET_PREFIX = "javax.servlet";
    private static final String JAKARTA_SERVLET_PREFIX = "jakarta.servlet";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        System.out.println("Transforming servlet imports from javax.servlet to jakarta.servlet in: " + sourcePath);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            // Add input source
            launcher.addInputResource(sourcePath);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Apply transformations
            transformServletImports(model, launcher);
            
            // Apply transformations to method parameter types
            transformMethodParameterTypes(model, launcher);
            
            // Apply transformations to type references in extends/implements clauses
            transformTypeReferences(model, launcher);
            
            // Output transformed code
            launcher.setSourceOutputDirectory(sourcePath);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformServletImports(CtModel model, Launcher launcher) {
        System.out.println("Transforming import statements...");
        
        // Get all imports in the model
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        for (CtImport ctImport : imports) {
            try {
                // Get the reference from the import
                Object importedRef = ctImport.getReference();
                if (importedRef instanceof CtTypeReference) {
                    CtTypeReference<?> importedType = (CtTypeReference<?>) importedRef;
                    String qualifiedName = importedType.getQualifiedName();
                    
                    // Check if this is a javax.servlet import
                    if (qualifiedName != null && qualifiedName.startsWith(JAVAX_SERVLET_PREFIX)) {
                        // Replace javax.servlet with jakarta.servlet
                        String newQualifiedName = qualifiedName.replace(JAVAX_SERVLET_PREFIX, JAKARTA_SERVLET_PREFIX);
                        
                        // Create new import reference
                        CtTypeReference<?> newImportedType = launcher.getFactory().createReference(newQualifiedName);
                        ctImport.setReference(newImportedType);
                        System.out.println("Transformed import: " + qualifiedName + " -> " + newQualifiedName);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to transform import: " + ctImport);
            }
        }
    }
    
    private static void transformMethodParameterTypes(CtModel model, Launcher launcher) {
        System.out.println("Transforming method parameter and type references...");
        
        // Find all method parameter types that reference javax.servlet
        model.getElements(new TypeFilter<>(CtTypeReference.class)).forEach(typeRef -> {
            try {
                String qualifiedName = typeRef.getQualifiedName();
                
                if (qualifiedName != null && qualifiedName.startsWith(JAVAX_SERVLET_PREFIX)) {
                    String newQualifiedName = qualifiedName.replace(JAVAX_SERVLET_PREFIX, JAKARTA_SERVLET_PREFIX);
                    
                    CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newQualifiedName);
                    
                    // Copy type arguments if present
                    if (!typeRef.getActualTypeArguments().isEmpty()) {
                        newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                    }
                    
                    typeRef.replace(newTypeRef);
                    System.out.println("Transformed type reference: " + qualifiedName + " -> " + newQualifiedName);
                }
            } catch (Exception e) {
                System.err.println("Failed to transform type reference: " + typeRef);
            }
        });
    }
    
    private static void transformTypeReferences(CtModel model, Launcher launcher) {
        System.out.println("Transforming type references in extends/implements clauses...");
        
        // Get all classes and interfaces
        List<CtType<?>> types = model.getElements(new TypeFilter<>(CtType.class));
        
        for (CtType<?> type : types) {
            try {
                // Check superclass
                if (type.getSuperclass() != null) {
                    String superClassName = type.getSuperclass().getQualifiedName();
                    if (superClassName != null && superClassName.startsWith(JAVAX_SERVLET_PREFIX)) {
                        String newSuperClassName = superClassName.replace(JAVAX_SERVLET_PREFIX, JAKARTA_SERVLET_PREFIX);
                        CtTypeReference<?> newSuperClass = launcher.getFactory().createReference(newSuperClassName);
                        type.setSuperclass(newSuperClass);
                        System.out.println("Transformed superclass: " + superClassName + " -> " + newSuperClassName);
                    }
                }
                
                // Check implemented interfaces
                Set<CtTypeReference<?>> interfaces = type.getSuperInterfaces();
                Set<CtTypeReference<?>> newInterfaces = new HashSet<>();
                boolean modified = false;
                
                for (CtTypeReference<?> iface : interfaces) {
                    String ifaceName = iface.getQualifiedName();
                    if (ifaceName != null && ifaceName.startsWith(JAVAX_SERVLET_PREFIX)) {
                        String newIfaceName = ifaceName.replace(JAVAX_SERVLET_PREFIX, JAKARTA_SERVLET_PREFIX);
                        CtTypeReference<?> newIface = launcher.getFactory().createReference(newIfaceName);
                        newInterfaces.add(newIface);
                        modified = true;
                        System.out.println("Transformed interface: " + ifaceName + " -> " + newIfaceName);
                    } else {
                        newInterfaces.add(iface);
                    }
                }
                
                if (modified) {
                    type.setSuperInterfaces(newInterfaces);
                }
            } catch (Exception e) {
                System.err.println("Failed to transform type: " + type.getQualifiedName());
            }
        }
    }
}