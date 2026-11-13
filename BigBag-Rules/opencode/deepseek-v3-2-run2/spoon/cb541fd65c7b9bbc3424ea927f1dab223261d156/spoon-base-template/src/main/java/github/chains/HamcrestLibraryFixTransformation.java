package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.Set;

/**
 * Generic Spoon transformation for hamcrest-library 2.2 breaking change.
 * 
 * Transformation rules:
 * 1. Matchers.emptyIterableOf(T.class) -> Matchers.emptyIterable()
 * 2. Common methods -> use CoreMatchers instead
 * 3. hasProperty() -> requires hamcrest dependency (warning only)
 * 
 * Projects must also update pom.xml to use hamcrest instead of hamcrest-library.
 */
public class HamcrestLibraryFixTransformation {
    
    public static void run(String sourceDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);
        
        launcher.addProcessor(new EmptyIterableOfFix());
        launcher.addProcessor(new CoreMatchersMigration());
        
        launcher.run();
    }
    
    static class EmptyIterableOfFix extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (!isMatchersMethod(invocation, "emptyIterableOf")) return;
            
            // Replace Matchers.emptyIterableOf(T.class) with Matchers.emptyIterable()
            CtTypeAccess<?> matchersAccess = getFactory().createTypeAccess(
                getFactory().Type().createReference("org.hamcrest.Matchers")
            );
            
            CtExecutableReference<?> emptyIterableRef = getFactory().Executable()
                .createReference(matchersAccess.getType(), null, "emptyIterable");
            
            CtInvocation<?> newInvocation = getFactory().createInvocation(
                matchersAccess, emptyIterableRef
            );
            
            invocation.replace(newInvocation);
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            return isMatchersMethod(candidate, "emptyIterableOf");
        }
    }
    
    static class CoreMatchersMigration extends AbstractProcessor<CtInvocation<?>> {
        private static final Set<String> CORE_METHODS = Set.of(
            "not", "notNullValue", "hasItems", "containsString", "equalTo",
            "is", "any", "allOf", "anyOf", "hasItem"
        );
        
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> exec = invocation.getExecutable();
            if (exec == null || !CORE_METHODS.contains(exec.getSimpleName())) return;
            
            if (!isMatchersMethod(invocation, exec.getSimpleName())) return;
            
            // Replace Matchers.method() with CoreMatchers.method()
            CtTypeAccess<?> coreMatchersAccess = getFactory().createTypeAccess(
                getFactory().Type().createReference("org.hamcrest.CoreMatchers")
            );
            invocation.getTarget().replace(coreMatchersAccess);
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> exec = candidate.getExecutable();
            if (exec == null) return false;
            
            return CORE_METHODS.contains(exec.getSimpleName()) && 
                   isMatchersMethod(candidate, exec.getSimpleName());
        }
    }
    
    private static boolean isMatchersMethod(CtInvocation<?> invocation, String methodName) {
        CtExecutableReference<?> exec = invocation.getExecutable();
        if (exec == null || !exec.getSimpleName().equals(methodName)) return false;
        
        CtTypeReference<?> declaringType = exec.getDeclaringType();
        return declaringType != null && 
               declaringType.getQualifiedName().equals("org.hamcrest.Matchers");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java HamcrestLibraryFixTransformation <source-dir>");
            System.err.println("\nFixes hamcrest-library 2.2 breaking change:");
            System.err.println("- Replaces emptyIterableOf(T.class) with emptyIterable()");
            System.err.println("- Migrates common methods to CoreMatchers");
            System.err.println("\nNOTE: Also update pom.xml to use hamcrest instead of hamcrest-library");
            System.exit(1);
        }
        
        run(args[0]);
        System.out.println("Transformation applied. Update pom.xml to use hamcrest dependency.");
    }
}