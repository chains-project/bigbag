package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying API transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        // Transformation 1: Replace StringID<T> with UID
        replaceStringIDWithUID(model);
        
        // Transformation 2: Remove type parameters from Entity<T>
        removeEntityTypeParameters(model);
        
        // Transformation 3: Fix DAO interface implementations
        fixDAOImplementations(model);
        
        // Apply the transformations
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    private static void replaceStringIDWithUID(CtModel model) {
        // Find all references to StringID and replace with UID
        model.getElements(new Filter<CtTypeReference<?>>() {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                if (element.getQualifiedName() != null && 
                    element.getQualifiedName().equals("com.premiumminds.billy.core.services.StringID")) {
                    
                    // Replace StringID<T> with UID
                    CtTypeReference<?> uidRef = element.getFactory().Type().createReference("com.premiumminds.billy.core.services.UID");
                    
                    // Try to replace
                    if (element.getParent() != null) {
                        try {
                            element.replace(uidRef);
                        } catch (Exception e) {
                            System.err.println("Warning: Could not replace StringID reference: " + e.getMessage());
                        }
                    }
                    
                    return true;
                }
                return false;
            }
        });
    }
    
    private static void removeEntityTypeParameters(CtModel model) {
        // Find all references to Entity<T> and remove the type parameter
        model.getElements(new Filter<CtTypeReference<?>>() {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                if (element.getQualifiedName() != null && 
                    element.getQualifiedName().equals("com.premiumminds.billy.core.services.entities.Entity") &&
                    element.getActualTypeArguments() != null && !element.getActualTypeArguments().isEmpty()) {
                    
                    // Create non-generic Entity reference
                    CtTypeReference<?> entityRef = element.getFactory().Type().createReference("com.premiumminds.billy.core.services.entities.Entity");
                    
                    // Try to replace
                    if (element.getParent() != null) {
                        try {
                            element.replace(entityRef);
                        } catch (Exception e) {
                            System.err.println("Warning: Could not replace Entity<T> reference: " + e.getMessage());
                        }
                    }
                    
                    return true;
                }
                return false;
            }
        });
        
        // Fix implements clauses like "implements Entity<T>"
        model.getElements(new Filter<CtClass<?>>() {
            @Override
            public boolean matches(CtClass<?> element) {
                boolean modified = false;
                List<CtTypeReference<?>> interfaces = new ArrayList<>(element.getSuperInterfaces());
                
                for (int i = 0; i < interfaces.size(); i++) {
                    CtTypeReference<?> iface = interfaces.get(i);
                    if (iface.getQualifiedName() != null && 
                        iface.getQualifiedName().equals("com.premiumminds.billy.core.services.entities.Entity") &&
                        iface.getActualTypeArguments() != null && !iface.getActualTypeArguments().isEmpty()) {
                        
                        // Replace with non-generic Entity
                        CtTypeReference<?> entityRef = element.getFactory().Type().createReference("com.premiumminds.billy.core.services.entities.Entity");
                        interfaces.set(i, entityRef);
                        modified = true;
                    }
                }
                
                if (modified) {
                    element.setSuperInterfaces(new java.util.HashSet<>(interfaces));
                }
                
                return false;
            }
        });
    }
    
    private static void fixDAOImplementations(CtModel model) {
        // Fix DAO implementations that use old DAO<TID, TInterface> pattern
        model.getElements(new Filter<CtClass<?>>() {
            @Override
            public boolean matches(CtClass<?> element) {
                for (CtTypeReference<?> iface : element.getSuperInterfaces()) {
                    if (iface.getQualifiedName() != null && 
                        iface.getQualifiedName().equals("com.premiumminds.billy.core.persistence.dao.DAO")) {
                        
                        // Check if it has two type parameters (old pattern: DAO<TID, TInterface>)
                        if (iface.getActualTypeArguments() != null && iface.getActualTypeArguments().size() == 2) {
                            // Old pattern: DAO<TID, TInterface>
                            // New pattern: DAO<T> where T extends BaseEntity
                            // We need to use the second type parameter (TInterface) as T
                            CtTypeReference<?> tInterface = iface.getActualTypeArguments().get(1);
                            
                            // Create new DAO reference with single type parameter
                            CtTypeReference<?> daoRef = element.getFactory().Type().createReference("com.premiumminds.billy.core.persistence.dao.DAO");
                            List<CtTypeReference<?>> newTypeArgs = new ArrayList<>();
                            newTypeArgs.add(tInterface);
                            daoRef.setActualTypeArguments(newTypeArgs);
                            
                            // Replace the interface reference
                            List<CtTypeReference<?>> interfaces = new ArrayList<>(element.getSuperInterfaces());
                            for (int i = 0; i < interfaces.size(); i++) {
                                if (interfaces.get(i).equals(iface)) {
                                    interfaces.set(i, daoRef);
                                    break;
                                }
                            }
                            element.setSuperInterfaces(new java.util.HashSet<>(interfaces));
                        }
                    }
                }
                return false;
            }
        });
    }
}