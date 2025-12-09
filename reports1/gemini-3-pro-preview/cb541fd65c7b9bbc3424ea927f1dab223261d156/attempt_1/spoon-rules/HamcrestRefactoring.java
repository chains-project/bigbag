package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.ArrayList;
import java.util.List;

public class HamcrestRefactoring {

    /**
     * Processor to migrate from org.hamcrest.Matchers to org.hamcrest.CoreMatchers.
     * Use CtCompilationUnit to ensure we capture both Imports and Code Usage safely.
     */
    public static class MatchersProcessor extends AbstractProcessor<CtCompilationUnit> {

        private static final String OLD_CLASS_QNAME = "org.hamcrest.Matchers";
        private static final String NEW_CLASS_SIMPLE = "CoreMatchers";
        private static final String NEW_CLASS_QNAME = "org.hamcrest.CoreMatchers";

        @Override
        public void process(CtCompilationUnit cu) {
            // 1. Process Imports (Header)
            // We iterate a copy to safely modify the underlying references if needed
            for (CtImport imp : new ArrayList<>(cu.getImports())) {
                processImport(imp);
            }

            // 2. Process Explicit Type Access in Body (e.g., Matchers.is(...))
            List<CtTypeAccess<?>> accesses = cu.getElements(new TypeFilter<>(CtTypeAccess.class));
            for (CtTypeAccess<?> access : accesses) {
                processTypeAccess(access);
            }
        }

        private void processImport(CtImport imp) {
            CtReference ref = imp.getReference();
            if (ref == null) return;

            // Case A: import org.hamcrest.Matchers;
            if (ref instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                if (isTargetType(typeRef)) {
                    updateTypeReference(typeRef);
                    System.out.println("Updated Type Import at " + imp.getPosition().getLine());
                }
            }
            // Case B: import static org.hamcrest.Matchers.is;
            else if (ref instanceof CtExecutableReference) {
                CtExecutableReference<?> execRef = (CtExecutableReference<?>) ref;
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                if (isTargetType(declaringType)) {
                    updateTypeReference(declaringType);
                    System.out.println("Updated Static Method Import at " + imp.getPosition().getLine());
                }
            }
            // Case C: import static org.hamcrest.Matchers.someField;
            else if (ref instanceof CtFieldReference) {
                CtFieldReference<?> fieldRef = (CtFieldReference<?>) ref;
                CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
                if (isTargetType(declaringType)) {
                    updateTypeReference(declaringType);
                    System.out.println("Updated Static Field Import at " + imp.getPosition().getLine());
                }
            }
        }

        private void processTypeAccess(CtTypeAccess<?> access) {
            CtTypeReference<?> accessedType = access.getAccessedType();
            if (isTargetType(accessedType)) {
                updateTypeReference(accessedType);
                System.out.println("Updated Explicit Type Access at " + access.getPosition().getLine());
            }
        }

        // Helper: Check if type matches org.hamcrest.Matchers
        private boolean isTargetType(CtTypeReference<?> typeRef) {
            if (typeRef == null) return false;
            String qName = typeRef.getQualifiedName();
            return qName != null && qName.equals(OLD_CLASS_QNAME);
        }

        // Helper: Rename type to CoreMatchers
        private void updateTypeReference(CtTypeReference<?> typeRef) {
            // We assume package "org.hamcrest" remains the same.
            // Just swapping the SimpleName is usually sufficient for Spoon to reprint correctly,
            // preserving the existing qualification style (fully qualified vs imported).
            typeRef.setSimpleName(NEW_CLASS_SIMPLE);
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/cb541fd65c7b9bbc3424ea927f1dab223261d156/jcabi-http/src/main/java/com/jcabi/http/mock/MkQueryMatchers.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/cb541fd65c7b9bbc3424ea927f1dab223261d156/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/cb541fd65c7b9bbc3424ea927f1dab223261d156/jcabi-http/src/main/java/com/jcabi/http/mock/MkQueryMatchers.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/cb541fd65c7b9bbc3424ea927f1dab223261d156/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for precise source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MatchersProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}