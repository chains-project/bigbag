package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new CloudResourceManagerV3MigrationProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

class CloudResourceManagerV3MigrationProcessor extends AbstractProcessor<CtType<?>> {
    
    private static final String OLD_MODEL_PACKAGE = "com.google.api.services.cloudresourcemanager.model";
    private static final String NEW_MODEL_PACKAGE = "com.google.api.services.cloudresourcemanager.v3.model";
    private static final String OLD_SERVICE_PACKAGE = "com.google.api.services.cloudresourcemanager";
    private static final String NEW_SERVICE_PACKAGE = "com.google.api.services.cloudresourcemanager.v3";
    
    private static final Set<String> REMOVED_CLASSES = Set.of(
        "OrgPolicy",
        "Constraint", 
        "BooleanConstraint",
        "ListConstraint",
        "BooleanPolicy",
        "ListPolicy",
        "RestoreDefault"
    );
    
    private static final Set<String> MIGRATED_CLASSES = Set.of(
        "Project",
        "Policy",
        "Operation",
        "Status",
        "Folder",
        "Organization",
        "Lien",
        "TagBinding",
        "TagKey",
        "TagValue",
        "Expr",
        "Binding",
        "AuditConfig",
        "AuditLogConfig",
        "Empty"
    );
    
    @Override
    public void process(CtType<?> type) {
        System.out.println("Processing type: " + type.getQualifiedName());
        
        migrateImports(type);
        migrateTypeReferences(type);
        migrateFieldAndMethodTypes(type);
    }
    
    private void migrateImports(CtType<?> type) {
        List<CtImport> importsToRemove = new ArrayList<>();
        List<CtImport> importsToAdd = new ArrayList<>();
        
        for (CtImport ctImport : type.getPosition().getCompilationUnit().getImports()) {
            String importStr = ctImport.getReference().toString();
            
            if (importStr.startsWith(OLD_MODEL_PACKAGE)) {
                System.out.println("  Found old model import: " + importStr);
                
                String className = importStr.substring(OLD_MODEL_PACKAGE.length() + 1);
                
                if (REMOVED_CLASSES.contains(className)) {
                    System.out.println("  WARNING: Class " + className + " appears to be removed in v3 API");
                    importsToRemove.add(ctImport);
                } else if (MIGRATED_CLASSES.contains(className) || !className.contains(".")) {
                    String newImport = NEW_MODEL_PACKAGE + "." + className;
                    System.out.println("  Migrating to: " + newImport);
                    
                    CtImport newImportObj = getFactory().createImport(
                        getFactory().createReference(newImport)
                    );
                    importsToAdd.add(newImportObj);
                    importsToRemove.add(ctImport);
                }
            } else if (importStr.startsWith(OLD_SERVICE_PACKAGE) && 
                      !importStr.startsWith(NEW_SERVICE_PACKAGE) &&
                      !importStr.contains(".model.")) {
                System.out.println("  Found old service import: " + importStr);
                
                if (importStr.equals(OLD_SERVICE_PACKAGE + ".CloudResourceManager") ||
                    importStr.equals(OLD_SERVICE_PACKAGE + ".CloudResourceManagerRequestInitializer")) {
                    String newImport = NEW_SERVICE_PACKAGE + "." + 
                        importStr.substring(OLD_SERVICE_PACKAGE.length() + 1);
                    System.out.println("  Migrating to: " + newImport);
                    
                    CtImport newImportObj = getFactory().createImport(
                        getFactory().createReference(newImport)
                    );
                    importsToAdd.add(newImportObj);
                    importsToRemove.add(ctImport);
                }
            }
        }
        
        for (CtImport importToRemove : importsToRemove) {
            type.getPosition().getCompilationUnit().removeImport(importToRemove);
        }
        
        for (CtImport importToAdd : importsToAdd) {
            type.getPosition().getCompilationUnit().addImport(importToAdd);
        }
    }
    
    private void migrateTypeReferences(CtType<?> type) {
        type.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))
            .forEach(this::migrateTypeReference);
    }
    
    private void migrateTypeReference(CtTypeReference<?> typeRef) {
        String qualifiedName = typeRef.getQualifiedName();
        
        if (qualifiedName.startsWith(OLD_MODEL_PACKAGE)) {
            String className = qualifiedName.substring(OLD_MODEL_PACKAGE.length() + 1);
            
            if (REMOVED_CLASSES.contains(className)) {
                System.out.println("  WARNING: Usage of removed class " + qualifiedName + 
                                 " at " + typeRef.getPosition());
            } else if (MIGRATED_CLASSES.contains(className) || !className.contains(".")) {
                String newQualifiedName = NEW_MODEL_PACKAGE + "." + className;
                System.out.println("  Migrating type reference: " + qualifiedName + " -> " + newQualifiedName);
                
                CtTypeReference<?> newTypeRef = getFactory().createReference(newQualifiedName);
                typeRef.replace(newTypeRef);
            }
        } else if (qualifiedName.startsWith(OLD_SERVICE_PACKAGE) && 
                  !qualifiedName.startsWith(NEW_SERVICE_PACKAGE) &&
                  !qualifiedName.contains(".model.")) {
            System.out.println("  Migrating service type reference: " + qualifiedName);
            
            if (qualifiedName.equals(OLD_SERVICE_PACKAGE + ".CloudResourceManager") ||
                qualifiedName.equals(OLD_SERVICE_PACKAGE + ".CloudResourceManagerRequestInitializer")) {
                String newQualifiedName = NEW_SERVICE_PACKAGE + "." + 
                    qualifiedName.substring(OLD_SERVICE_PACKAGE.length() + 1);
                
                CtTypeReference<?> newTypeRef = getFactory().createReference(newQualifiedName);
                typeRef.replace(newTypeRef);
            }
        }
    }
    
    private void migrateFieldAndMethodTypes(CtType<?> type) {
        type.getFields().forEach(field -> {
            CtTypeReference<?> fieldType = field.getType();
            migrateTypeReference(fieldType);
        });
        
        type.getMethods().forEach(method -> {
            CtTypeReference<?> returnType = method.getType();
            migrateTypeReference(returnType);
            
            method.getParameters().forEach(param -> {
                CtTypeReference<?> paramType = param.getType();
                migrateTypeReference(paramType);
            });
        });
    }
    
    @Override
    public boolean isToBeProcessed(CtType<?> candidate) {
        return true;
    }
}