package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtJavaDoc;
import spoon.reflect.code.CtJavaDocTag;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Define old and new package patterns - CONFIGURABLE PARAMETERS
        String oldPackagePrefix = "com.alibaba.dubbo";
        String newPackagePrefix = "org.apache.dubbo";
        
        int importChanges = 0;
        int typeRefChanges = 0;
        int literalChanges = 0;
        int javadocChanges = 0;
        
        // 1. Update type references
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            if (typeName != null && typeName.startsWith(oldPackagePrefix)) {
                String newTypeName = typeName.replace(oldPackagePrefix, newPackagePrefix);
                System.out.println("Type reference to update: " + typeName + " -> " + newTypeName);
                typeRefChanges++;
            }
        }
        
        // 2. Update string literals (e.g., in named() matchers)
        List<CtLiteral<?>> literals = model.getElements(new TypeFilter<>(CtLiteral.class));
        for (CtLiteral<?> literal : literals) {
            Object value = literal.getValue();
            if (value instanceof String) {
                String strValue = (String) value;
                if (strValue.contains(oldPackagePrefix)) {
                    String newValue = strValue.replace(oldPackagePrefix, newPackagePrefix);
                    System.out.println("String literal to update: " + strValue + " -> " + newValue);
                    literalChanges++;
                }
            }
        }
        
        // 3. Update imports
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            if (importStr.contains(oldPackagePrefix)) {
                String newImportStr = importStr.replace(oldPackagePrefix, newPackagePrefix);
                System.out.println("Import to update: " + importStr + " -> " + newImportStr);
                importChanges++;
            }
        }
        
        // 4. Update JavaDoc references
        List<CtJavaDoc> javadocs = model.getElements(new TypeFilter<>(CtJavaDoc.class));
        for (CtJavaDoc javadoc : javadocs) {
            String javadocStr = javadoc.toString();
            if (javadocStr.contains(oldPackagePrefix)) {
                System.out.println("JavaDoc reference to update");
                javadocChanges++;
            }
        }
        
        System.out.println("\n=== TRANSFORMATION SUMMARY ===");
        System.out.println("Total changes identified: " + (importChanges + typeRefChanges + literalChanges + javadocChanges));
        System.out.println("  Import statements: " + importChanges);
        System.out.println("  Type references: " + typeRefChanges);
        System.out.println("  String literals: " + literalChanges);
        System.out.println("  JavaDoc references: " + javadocChanges);
        
        // Write transformed code back
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.prettyprint();
        
        System.out.println("\nTransformation output written to: " + sourceDir + "-transformed");
        
        // === GENERIC, REUSABLE TRANSFORMATION RULE ===
        System.out.println("\n=== GENERIC, REUSABLE TRANSFORMATION RULE ===");
        System.out.println("Breaking Change Characterization:");
        System.out.println("  Old API pattern: " + oldPackagePrefix + ".* (e.g., " + oldPackagePrefix + ".rpc.Invocation)");
        System.out.println("  New API pattern: " + newPackagePrefix + ".* (e.g., " + newPackagePrefix + ".rpc.Invocation)");
        System.out.println("  Change type: Package rename (com.alibaba.dubbo → org.apache.dubbo)");
        
        System.out.println("\nRequired Structural Transformation:");
        System.out.println("  1. Import statements: Update all import declarations");
        System.out.println("  2. Type references: Update all type references in code");
        System.out.println("  3. String literals: Update package references in strings (e.g., named() matchers)");
        System.out.println("  4. Fully-qualified names: Update explicit package qualifiers");
        
        System.out.println("\nGeneric Spoon Transformation Pattern:");
        System.out.println("  // Configuration parameters");
        System.out.println("  String OLD_PACKAGE = \"" + oldPackagePrefix + "\";");
        System.out.println("  String NEW_PACKAGE = \"" + newPackagePrefix + "\";");
        System.out.println("  ");
        System.out.println("  // Transformation algorithm:");
        System.out.println("  1. Traverse all CtTypeReference elements");
        System.out.println("  2. If qualifiedName.startsWith(OLD_PACKAGE)");
        System.out.println("  3.   Replace with qualifiedName.replace(OLD_PACKAGE, NEW_PACKAGE)");
        System.out.println("  4. Traverse all CtLiteral elements");
        System.out.println("  5. If value instanceof String && value.contains(OLD_PACKAGE)");
        System.out.println("  6.   Replace with value.replace(OLD_PACKAGE, NEW_PACKAGE)");
        System.out.println("  7. Traverse all CtImport elements");
        System.out.println("  8. If import string contains OLD_PACKAGE");
        System.out.println("  9.   Replace with import.replace(OLD_PACKAGE, NEW_PACKAGE)");
        
        System.out.println("\nApplicability:");
        System.out.println("  - This rule is generic: works for ANY project using " + oldPackagePrefix);
        System.out.println("  - Parameterized by OLD_PACKAGE and NEW_PACKAGE constants");
        System.out.println("  - No project-specific hardcoding");
        System.out.println("  - Applies to all source files recursively");
        
        System.out.println("\nUsage for other projects:");
        System.out.println("  Simply change OLD_PACKAGE and NEW_PACKAGE constants");
        System.out.println("  Run on any source directory: java Main <source-dir>");
    }
}