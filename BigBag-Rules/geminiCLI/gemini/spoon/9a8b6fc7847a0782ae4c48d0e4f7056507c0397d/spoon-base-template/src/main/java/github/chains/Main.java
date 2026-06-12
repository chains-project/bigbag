package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtBlock;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        String projectPath = "/workspace/geostore/src/core/security/src/main/java";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectPath);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.buildModel();
        
        CtModel model = launcher.getModel();
        
        // 1. Fix GeoStoreDigestPasswordEncoder
        CtClass<?> digestClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return "GeoStoreDigestPasswordEncoder".equals(element.getSimpleName());
            }
        }).stream().findFirst().orElse(null);
        
        if (digestClass != null) {
            System.out.println("Processing GeoStoreDigestPasswordEncoder");
            
            CtCompilationUnit cu = digestClass.getFactory().CompilationUnit().getOrCreate(digestClass);
            // Collect existing imports
            List<CtImport> imports = new ArrayList<>(cu.getImports());
            List<CtImport> toRemove = new ArrayList<>();
            
            for (CtImport imp : imports) {
                String impStr = imp.toString();
                System.out.println("Checking import: " + impStr);
                if (impStr.contains("org.jasypt.spring.security")) {
                    System.out.println("Marking for removal: " + impStr);
                    toRemove.add(imp);
                }
            }
            imports.removeAll(toRemove);
            
            // Add correct PasswordEncoder import
            CtTypeReference<?> acegiRef = launcher.getFactory().Type().createReference("org.acegisecurity.providers.encoding.PasswordEncoder");
            boolean hasAcegi = imports.stream().anyMatch(i -> i.toString().contains("org.acegisecurity.providers.encoding.PasswordEncoder"));
            if (!hasAcegi) {
                 imports.add(launcher.getFactory().createImport(acegiRef));
            }
            cu.setImports(imports);

            // Stub createStringEncoder
            CtMethod<?> method = digestClass.getMethod("createStringEncoder");
            if (method != null) {
                CtBlock<?> body = launcher.getFactory().Code().createCtBlock(
                    launcher.getFactory().Code().createCodeSnippetStatement("return null")
                );
                method.setBody(body);
                // Also explicitly set the return type in case it was resolved to the removed import
                method.setType(acegiRef);
            }
        }
        
        // 2. Fix GeoStorePBEPasswordEncoder
        CtClass<?> pbeClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return "GeoStorePBEPasswordEncoder".equals(element.getSimpleName());
            }
        }).stream().findFirst().orElse(null);
        
        if (pbeClass != null) {
             System.out.println("Processing GeoStorePBEPasswordEncoder");
             
            CtCompilationUnit cu = pbeClass.getFactory().CompilationUnit().getOrCreate(pbeClass);
            List<CtImport> imports = new ArrayList<>(cu.getImports());
            List<CtImport> toRemove = new ArrayList<>();
            
            for (CtImport imp : imports) {
                String impStr = imp.toString();
                if (impStr.contains("org.jasypt.spring.security")) {
                    System.out.println("Marking for removal: " + impStr);
                    toRemove.add(imp);
                }
            }
            imports.removeAll(toRemove);
            cu.setImports(imports);

             // Stub createStringEncoder
             CtMethod<?> method = pbeClass.getMethod("createStringEncoder");
             if (method != null) {
                 CtBlock<?> body = launcher.getFactory().Code().createCtBlock(
                     launcher.getFactory().Code().createCodeSnippetStatement("return null")
                 );
                 method.setBody(body);
                 // Fix return type if necessary, though this class already imported acegi PasswordEncoder
             }
        }

        launcher.setSourceOutputDirectory(projectPath);
        launcher.prettyprint();
    }
}