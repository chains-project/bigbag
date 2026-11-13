package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtParameter;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Main {
    
    // Mapping of old package names to new package names for Dropwizard 4.0.0
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Common package migrations from Dropwizard 2.x to 4.x
        PACKAGE_MAPPINGS.put("io.dropwizard.setup.", "io.dropwizard.core.setup.");
        PACKAGE_MAPPINGS.put("io.dropwizard.Application", "io.dropwizard.core.Application");
        PACKAGE_MAPPINGS.put("io.dropwizard.Configuration", "io.dropwizard.core.Configuration");
        PACKAGE_MAPPINGS.put("io.dropwizard.Bundle", "io.dropwizard.core.ConfiguredBundle");
        PACKAGE_MAPPINGS.put("io.dropwizard.ConfiguredBundle", "io.dropwizard.core.ConfiguredBundle");
        PACKAGE_MAPPINGS.put("io.dropwizard.cli.", "io.dropwizard.core.cli.");
        PACKAGE_MAPPINGS.put("io.dropwizard.configuration.", "io.dropwizard.core.configuration.");
        PACKAGE_MAPPINGS.put("io.dropwizard.jackson.", "io.dropwizard.core.jackson.");
        PACKAGE_MAPPINGS.put("io.dropwizard.jersey.", "io.dropwizard.core.jersey.");
        PACKAGE_MAPPINGS.put("io.dropwizard.jetty.", "io.dropwizard.core.jetty.");
        PACKAGE_MAPPINGS.put("io.dropwizard.lifecycle.", "io.dropwizard.core.lifecycle.");
        PACKAGE_MAPPINGS.put("io.dropwizard.logging.", "io.dropwizard.core.logging.");
        PACKAGE_MAPPINGS.put("io.dropwizard.metrics.", "io.dropwizard.core.metrics.");
        PACKAGE_MAPPINGS.put("io.dropwizard.request.logging.", "io.dropwizard.core.request.logging.");
        PACKAGE_MAPPINGS.put("io.dropwizard.servlets.", "io.dropwizard.core.servlets.");
        PACKAGE_MAPPINGS.put("io.dropwizard.util.", "io.dropwizard.core.util.");
        PACKAGE_MAPPINGS.put("io.dropwizard.validation.", "io.dropwizard.core.validation.");
        
        // Jakarta EE migration: javax -> jakarta
        PACKAGE_MAPPINGS.put("javax.ws.rs.", "jakarta.ws.rs.");
        PACKAGE_MAPPINGS.put("javax.annotation.", "jakarta.annotation.");
        PACKAGE_MAPPINGS.put("javax.validation.", "jakarta.validation.");
        PACKAGE_MAPPINGS.put("javax.servlet.", "jakarta.servlet.");
        PACKAGE_MAPPINGS.put("javax.inject.", "jakarta.inject.");
        
        // Jackson JAX-RS to Jakarta RS
        PACKAGE_MAPPINGS.put("com.fasterxml.jackson.jaxrs.", "com.fasterxml.jackson.jakarta.rs.");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms Dropwizard 2.x code to be compatible with Dropwizard 4.x");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transforming Dropwizard 2.x imports to Dropwizard 4.x package structure...");
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add input source directory
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Track statistics
        int importsFixed = 0;
        int typeRefsFixed = 0;
        int methodSignaturesFixed = 0;
        
        // 1. Fix imports
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
                String oldPkg = mapping.getKey();
                String newPkg = mapping.getValue();
                
                if (importStr.contains(oldPkg)) {
                    // Handle exact class matches (e.g., io.dropwizard.Application)
                    if (!oldPkg.endsWith(".")) {
                        // Check if this import exactly matches the old package
                        String importPattern = "import " + oldPkg + ";";
                        if (importStr.equals(importPattern)) {
                            try {
                                imp.setReference(imp.getFactory().createReference(newPkg));
                                importsFixed++;
                                System.out.println("Updated import: " + importStr + " -> import " + newPkg + ";");
                            } catch (Exception e) {
                                System.err.println("Failed to update import: " + importStr);
                            }
                        }
                    }
                    // Handle package imports (e.g., io.dropwizard.setup.*)
                    else if (oldPkg.endsWith(".")) {
                        // Check if this import starts with the old package
                        if (importStr.startsWith("import " + oldPkg)) {
                            try {
                                String importedType = importStr.substring(7, importStr.length() - 1);
                                String newImport;
                                if (importedType.endsWith(".*")) {
                                    // Package import with wildcard
                                    String pkg = importedType.substring(0, importedType.length() - 2);
                                    String newPkgFull = pkg.replace(oldPkg.substring(0, oldPkg.length() - 1), 
                                                                  newPkg.substring(0, newPkg.length() - 1));
                                    newImport = newPkgFull + ".*";
                                } else {
                                    // Specific class import
                                    newImport = importedType.replace(oldPkg.substring(0, oldPkg.length() - 1), 
                                                                   newPkg.substring(0, newPkg.length() - 1));
                                }
                                imp.setReference(imp.getFactory().createReference(newImport));
                                importsFixed++;
                                System.out.println("Updated import: " + importStr + " -> import " + newImport + ";");
                            } catch (Exception e) {
                                System.err.println("Failed to update import: " + importStr);
                            }
                        }
                    }
                }
            }
        }
        
        // 2. Fix type references in code
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null) {
                for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
                    String oldPkg = mapping.getKey();
                    String newPkg = mapping.getValue();
                    
                    // Check for exact class match
                    if (!oldPkg.endsWith(".") && qualifiedName.equals(oldPkg)) {
                        try {
                            CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newPkg);
                            typeRef.replace(newTypeRef);
                            typeRefsFixed++;
                            System.out.println("Updated type reference: " + qualifiedName + " -> " + newPkg);
                        } catch (Exception e) {
                            // Try alternative approach for type references in extends/implements
                            try {
                                if (typeRef.getParent() instanceof CtType) {
                                    CtType<?> parentType = (CtType<?>) typeRef.getParent();
                                    if (parentType.getSuperclass() != null && 
                                        parentType.getSuperclass().equals(typeRef)) {
                                        parentType.setSuperclass(typeRef.getFactory().createReference(newPkg));
                                        typeRefsFixed++;
                                        System.out.println("Updated superclass: " + qualifiedName + " -> " + newPkg);
                                    }
                                }
                            } catch (Exception e2) {
                                System.err.println("Failed to update type reference: " + qualifiedName);
                            }
                        }
                    }
                    // Check for classes in migrated packages
                    else if (oldPkg.endsWith(".") && qualifiedName.startsWith(oldPkg)) {
                        String newQualifiedName = qualifiedName.replace(oldPkg, newPkg);
                        try {
                            CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newQualifiedName);
                            typeRef.replace(newTypeRef);
                            typeRefsFixed++;
                            System.out.println("Updated type reference: " + qualifiedName + " -> " + newQualifiedName);
                        } catch (Exception e) {
                            System.err.println("Failed to update type reference in package: " + qualifiedName);
                        }
                    }
                }
            }
        }
        
        // 3. Update method/constructor signatures that use Environment type
        // Find constructors and methods that use Environment parameters
        List<CtConstructor<?>> constructors = model.getElements(new TypeFilter<>(CtConstructor.class));
        for (CtConstructor<?> constructor : constructors) {
            for (CtParameter<?> param : constructor.getParameters()) {
                String paramType = param.getType().getQualifiedName();
                if (paramType != null && paramType.equals("io.dropwizard.setup.Environment")) {
                    try {
                        param.setType(param.getFactory().createReference("io.dropwizard.core.setup.Environment"));
                        methodSignaturesFixed++;
                        System.out.println("Updated constructor parameter type: " + paramType + " -> io.dropwizard.core.setup.Environment");
                    } catch (Exception e) {
                        System.err.println("Failed to update constructor parameter: " + paramType);
                    }
                }
            }
        }
        
        List<CtMethod<?>> methods = model.getElements(new TypeFilter<>(CtMethod.class));
        for (CtMethod<?> method : methods) {
            // Check return type
            String returnType = method.getType().getQualifiedName();
            if (returnType != null && returnType.equals("io.dropwizard.setup.Environment")) {
                try {
                    method.setType(method.getFactory().createReference("io.dropwizard.core.setup.Environment"));
                    methodSignaturesFixed++;
                    System.out.println("Updated method return type: " + returnType + " -> io.dropwizard.core.setup.Environment");
                } catch (Exception e) {
                    System.err.println("Failed to update method return type: " + returnType);
                }
            }
            
            // Check parameters
            for (CtParameter<?> param : method.getParameters()) {
                String paramType = param.getType().getQualifiedName();
                if (paramType != null && paramType.equals("io.dropwizard.setup.Environment")) {
                    try {
                        param.setType(param.getFactory().createReference("io.dropwizard.core.setup.Environment"));
                        methodSignaturesFixed++;
                        System.out.println("Updated method parameter type: " + paramType + " -> io.dropwizard.core.setup.Environment");
                    } catch (Exception e) {
                        System.err.println("Failed to update method parameter: " + paramType);
                    }
                }
            }
        }
        
        System.out.println("\nTransformation Summary:");
        System.out.println("Imports fixed: " + importsFixed);
        System.out.println("Type references fixed: " + typeRefsFixed);
        System.out.println("Method/constructor signatures fixed: " + methodSignaturesFixed);
        System.out.println("Total changes: " + (importsFixed + typeRefsFixed + methodSignaturesFixed));
        
        // Output the transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed successfully!");
        System.out.println("Note: You may need to update your pom.xml to use Dropwizard 4.x dependencies:");
        System.out.println("  - Change dropwizard-core version to 4.0.0");
        System.out.println("  - Change dropwizard-client version to 4.0.0 (already set)");
        System.out.println("  - Update other Dropwizard dependencies to 4.0.0 as needed");
    }
}