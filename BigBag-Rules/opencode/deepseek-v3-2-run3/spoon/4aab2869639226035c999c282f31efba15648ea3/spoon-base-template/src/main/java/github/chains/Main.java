package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        // Find and fix Storages class usage
        fixStoragesUsage(model);
        
        // Apply changes
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    private static void fixStoragesUsage(CtModel model) {
        // Find all constructor calls to Storages class
        List<CtConstructorCall<?>> storagesCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> call) {
                CtTypeReference<?> typeRef = call.getType();
                if (typeRef != null) {
                    String qualifiedName = typeRef.getQualifiedName();
                    return qualifiedName != null && qualifiedName.equals("com.artipie.asto.factory.Storages");
                }
                return false;
            }
        });
        
        System.out.println("Found " + storagesCalls.size() + " Storages constructor calls");
        
        // Replace Storages constructor calls with StoragesLoader
        for (CtConstructorCall<?> call : storagesCalls) {
            System.out.println("Found Storages constructor call at: " + call.getPosition());
            
            // Replace the type reference
            CtTypeReference<?> newTypeRef = call.getFactory().createReference("com.artipie.asto.factory.StoragesLoader");
            call.setType(newTypeRef);
            
            // Look for parent invocation by checking parent chain
            CtInvocation<?> parentInvocation = findParentInvocation(call);
            
            if (parentInvocation != null && "newStorage".equals(parentInvocation.getExecutable().getSimpleName())) {
                System.out.println("Found newStorage() call, changing to newObject()");
                // Change method name to newObject
                parentInvocation.getExecutable().setSimpleName("newObject");
                
                // Check if we need to wrap YamlMapping in Config.YamlStorageConfig
                List<CtExpression<?>> arguments = parentInvocation.getArguments();
                if (arguments.size() == 2) {
                    CtExpression<?> secondArg = arguments.get(1);
                    CtTypeReference<?> exprType = secondArg.getType();
                    if (exprType != null && exprType.getQualifiedName() != null && 
                        exprType.getQualifiedName().equals("com.amihaiemil.eoyaml.YamlMapping")) {
                        System.out.println("Wrapping YamlMapping in Config.YamlStorageConfig");
                        // Wrap YamlMapping in Config.YamlStorageConfig
                        CtConstructorCall<?> wrapperCall = 
                            call.getFactory().createConstructorCall(
                                call.getFactory().createReference("com.artipie.asto.factory.Config$YamlStorageConfig"),
                                secondArg
                            );
                        List<CtExpression<?>> newArgs = new ArrayList<>();
                        newArgs.add(arguments.get(0));
                        newArgs.add(wrapperCall);
                        parentInvocation.setArguments(newArgs);
                    }
                }
            }
        }
        
        // Update imports
        updateImports(model);
    }
    
    private static CtInvocation<?> findParentInvocation(CtConstructorCall<?> call) {
        // Check if the call's parent is an invocation
        Object parent = call.getParent();
        return parent instanceof CtInvocation ? (CtInvocation<?>) parent : null;
    }
    
    private static void updateImports(CtModel model) {
        List<CtType<?>> types = model.getElements(new TypeFilter<CtType<?>>(CtType.class));
        for (CtType<?> type : types) {
            List<CtImport> imports = new ArrayList<>(type.getPosition().getCompilationUnit().getImports());
            boolean hasStoragesLoader = false;
            List<CtImport> newImports = new ArrayList<>();
            
            for (CtImport imp : imports) {
                if (imp.getReference() != null) {
                    String importStr = imp.getReference().toString();
                    if (importStr.equals("com.artipie.asto.factory.Storages")) {
                        System.out.println("Removing import: " + importStr);
                        continue;
                    }
                    if (importStr.equals("com.artipie.asto.factory.StoragesLoader")) {
                        hasStoragesLoader = true;
                    }
                }
                newImports.add(imp);
            }
            
            // Add StoragesLoader import if not already present
            if (!hasStoragesLoader && !newImports.isEmpty()) {
                CtTypeReference<?> storagesLoaderRef = type.getFactory().createReference("com.artipie.asto.factory.StoragesLoader");
                newImports.add(type.getFactory().createImport(storagesLoaderRef));
                System.out.println("Adding import: com.artipie.asto.factory.StoragesLoader");
            }
            
            // Update imports if we made changes
            if (newImports.size() != imports.size()) {
                type.getPosition().getCompilationUnit().getImports().clear();
                type.getPosition().getCompilationUnit().getImports().addAll(newImports);
            }
        }
    }
}