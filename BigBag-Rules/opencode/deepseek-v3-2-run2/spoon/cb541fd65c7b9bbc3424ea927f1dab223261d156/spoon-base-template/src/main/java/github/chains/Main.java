package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Generic Spoon transformation to fix hamcrest-library 2.2 breaking change.
 * 
 * Problem: hamcrest-library-2.2.jar is deprecated/empty. The Matchers class
 * is now in hamcrest.jar, not hamcrest-library.jar.
 * 
 * This transformation handles common cases:
 * 1. emptyIterableOf(T.class) -> emptyIterable()
 * 2. Methods available in CoreMatchers -> use CoreMatchers
 * 3. hasProperty() -> requires hamcrest dependency (not fixed here)
 * 
 * Usage: java -jar transformation.jar <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("\nThis transformation fixes hamcrest-library 2.2 breaking change.");
            System.err.println("Projects must also update pom.xml:");
            System.err.println("  Replace hamcrest-library dependency with hamcrest");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Fixing hamcrest-library 2.2 breaking change in: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);
        
        launcher.addProcessor(new HamcrestLibraryFixer());
        
        launcher.run();
        
        System.out.println("\nTransformation complete. IMPORTANT:");
        System.out.println("1. Update pom.xml: replace hamcrest-library with hamcrest dependency");
        System.out.println("2. Or add explicit hamcrest dependency if hasProperty() is used");
        System.out.println("3. Test compilation and tests");
    }
    
    static class HamcrestLibraryFixer extends AbstractProcessor<CtCompilationUnit> {
        // Methods that exist in both Matchers and CoreMatchers
        private static final Set<String> CORE_COMPATIBLE_METHODS = Set.of(
            "allOf", "anyOf", "both", "either", "describedAs", "everyItem",
            "is", "isA", "anything", "hasItem", "hasItems", "equalTo", 
            "equalToObject", "any", "instanceOf", "not", "notNullValue",
            "nullValue", "sameInstance", "theInstance", "containsString",
            "containsStringIgnoringCase", "startsWith", "startsWithIgnoringCase",
            "endsWith", "endsWithIgnoringCase", "matchesRegex"
        );
        
        @Override
        public void process(CtCompilationUnit cu) {
            boolean needsCoreMatchersImport = false;
            boolean needsMatchersImport = false;
            List<CtImport> importsToAdd = new ArrayList<>();
            
            // Find all Matchers method invocations
            List<CtInvocation<?>> invocations = cu.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation<?> invocation : invocations) {
                CtExecutableReference<?> exec = invocation.getExecutable();
                if (exec == null) continue;
                
                CtTypeReference<?> declaringType = exec.getDeclaringType();
                if (declaringType == null) continue;
                
                if (declaringType.getQualifiedName().equals("org.hamcrest.Matchers")) {
                    String methodName = exec.getSimpleName();
                    
                    if (methodName.equals("emptyIterableOf")) {
                        fixEmptyIterableOf(invocation);
                        needsMatchersImport = true; // emptyIterable() is in Matchers
                    } else if (CORE_COMPATIBLE_METHODS.contains(methodName)) {
                        replaceWithCoreMatchers(invocation);
                        needsCoreMatchersImport = true;
                    } else if (methodName.equals("hasProperty")) {
                        // hasProperty requires Matchers from hamcrest.jar
                        needsMatchersImport = true;
                        System.out.println("  WARNING: hasProperty() at " + invocation.getPosition() + 
                                         " requires hamcrest dependency (not hamcrest-library)");
                    } else {
                        // Unknown method, keep using Matchers
                        needsMatchersImport = true;
                        System.out.println("  INFO: Keeping Matchers." + methodName + 
                                         " at " + invocation.getPosition());
                    }
                }
            }
            
            // Update imports
            if (needsCoreMatchersImport) {
                addImportIfMissing(cu, "org.hamcrest.CoreMatchers");
            }
            if (needsMatchersImport) {
                addImportIfMissing(cu, "org.hamcrest.Matchers");
            }
            
            // Remove duplicate imports if both Matchers and CoreMatchers are present
            // (they often are after transformation)
            deduplicateImports(cu);
        }
        
        private void fixEmptyIterableOf(CtInvocation<?> invocation) {
            // Matchers.emptyIterableOf(T.class) -> Matchers.emptyIterable()
            CtTypeAccess<?> matchersAccess = getFactory().createTypeAccess(
                getFactory().Type().createReference("org.hamcrest.Matchers")
            );
            
            CtExecutableReference<?> emptyIterableRef = getFactory().Executable()
                .createReference(matchersAccess.getType(), null, "emptyIterable");
            
            CtInvocation<?> newInvocation = getFactory().createInvocation(
                matchersAccess, emptyIterableRef
            );
            
            invocation.replace(newInvocation);
            System.out.println("  Fixed: emptyIterableOf -> emptyIterable at " + invocation.getPosition());
        }
        
        private void replaceWithCoreMatchers(CtInvocation<?> invocation) {
            CtTypeAccess<?> coreMatchersAccess = getFactory().createTypeAccess(
                getFactory().Type().createReference("org.hamcrest.CoreMatchers")
            );
            invocation.getTarget().replace(coreMatchersAccess);
            System.out.println("  Fixed: Matchers." + invocation.getExecutable().getSimpleName() + 
                             " -> CoreMatchers." + invocation.getExecutable().getSimpleName() +
                             " at " + invocation.getPosition());
        }
        
        private void addImportIfMissing(CtCompilationUnit cu, String importName) {
            for (CtImport imp : cu.getImports()) {
                if (imp.toString().contains(importName)) {
                    return; // Already imported
                }
            }
            
            CtTypeReference<?> typeRef = getFactory().Type().createReference(importName);
            cu.getImports().add(getFactory().createImport(typeRef));
        }
        
        private void deduplicateImports(CtCompilationUnit cu) {
            List<CtImport> uniqueImports = new ArrayList<>();
            Set<String> importStrings = new HashSet<>();
            
            for (CtImport imp : cu.getImports()) {
                String importStr = imp.toString();
                if (!importStrings.contains(importStr)) {
                    importStrings.add(importStr);
                    uniqueImports.add(imp);
                }
            }
            
            cu.getImports().clear();
            cu.getImports().addAll(uniqueImports);
        }
    }
}