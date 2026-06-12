package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Get factory from launcher
            spoon.reflect.factory.Factory factory = launcher.getFactory();
            
            // Transformation 1: Fix StringUtils imports and references
            fixStringUtilsImports(model, factory);
            
            // Transformation 2: Fix ExecutorService.getInstance() calls  
            // We'll handle this with a simpler approach - just update the code manually
            // since Spoon API for complex transformations is tricky
            
            // Output transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void fixStringUtilsImports(CtModel model, spoon.reflect.factory.Factory factory) {
        System.out.println("Fixing StringUtils imports...");
        
        // Find all imports of liquibase.util.StringUtils
        List<CtImport> imports = Query.getElements(factory, new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport ctImport) {
                if (ctImport.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                    String qualifiedName = typeRef.getQualifiedName();
                    return "liquibase.util.StringUtils".equals(qualifiedName);
                }
                return false;
            }
        });
        
        for (CtImport imp : imports) {
            System.out.println("  Found import: " + imp.getReference());
            // Replace the import
            CtTypeReference<?> newTypeRef = factory.Type().createReference("liquibase.repackaged.org.apache.commons.lang3.StringUtils");
            imp.setReference(newTypeRef);
            System.out.println("  Changed to: " + newTypeRef.getQualifiedName());
        }
        
        // Also find any fully-qualified references to liquibase.util.StringUtils in code
        List<CtTypeAccess> typeAccesses = Query.getElements(factory, new TypeFilter<CtTypeAccess>(CtTypeAccess.class) {
            @Override
            public boolean matches(CtTypeAccess typeAccess) {
                CtTypeReference<?> typeRef = typeAccess.getAccessedType();
                return typeRef != null && "liquibase.util.StringUtils".equals(typeRef.getQualifiedName());
            }
        });
        
        for (CtTypeAccess typeAccess : typeAccesses) {
            System.out.println("  Found type access: " + typeAccess.getAccessedType().getQualifiedName());
            CtTypeReference<?> newTypeRef = factory.Type().createReference("liquibase.repackaged.org.apache.commons.lang3.StringUtils");
            typeAccess.setAccessedType(newTypeRef);
            System.out.println("  Changed to: " + newTypeRef.getQualifiedName());
        }
    }
}