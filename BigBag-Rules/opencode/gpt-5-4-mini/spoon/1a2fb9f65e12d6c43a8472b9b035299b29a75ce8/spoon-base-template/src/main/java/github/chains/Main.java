package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        Path sourceDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : sourceDir.resolveSibling(sourceDir.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new ValidationNamespaceMigrationProcessor());
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    public static final class ValidationNamespaceMigrationProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(CtElement element) {
            if (element instanceof CtImport) {
                rewriteImport((CtImport) element);
            }

            if (element instanceof CtReference) {
                rewriteReference((CtReference) element);
            }
        }

        private void rewriteImport(CtImport ctImport) {
            CtReference reference = ctImport.getReference();
            if (reference == null) {
                return;
            }

            String qualifiedName = reference instanceof CtTypeReference
                    ? ((CtTypeReference<?>) reference).getQualifiedName()
                    : reference.getSimpleName();
            if (qualifiedName == null || !qualifiedName.startsWith(OLD_PREFIX)) {
                return;
            }

            ctImport.replace(getFactory().Type().createImport(getFactory().Type().createReference(rewriteName(qualifiedName))));

            CtCompilationUnit compilationUnit = ctImport.getPosition() != null ? ctImport.getPosition().getCompilationUnit() : null;
            if (compilationUnit != null) {
                for (CtImport existingImport : compilationUnit.getImports()) {
                    CtReference existingReference = existingImport.getReference();
                    if (existingReference == null) {
                        continue;
                    }
                    String existingName = existingReference instanceof CtTypeReference
                            ? ((CtTypeReference<?>) existingReference).getQualifiedName()
                            : existingReference.getSimpleName();
                    if (existingName != null && existingName.startsWith(OLD_PREFIX)) {
                        existingImport.delete();
                    }
                }
            }
        }

        private void rewriteReference(CtReference reference) {
            if (reference == null) {
                return;
            }

            if (reference instanceof CtPackageReference) {
                String simpleName = reference.getSimpleName();
                if (simpleName != null && simpleName.startsWith(OLD_PREFIX)) {
                    reference.replace(getFactory().Package().createReference(rewriteName(simpleName)));
                }
                return;
            }

            if (reference instanceof CtTypeReference) {
                CtTypeReference<?> typeReference = (CtTypeReference<?>) reference;
                String qualifiedName = typeReference.getQualifiedName();
                if (qualifiedName == null || !qualifiedName.startsWith(OLD_PREFIX)) {
                    return;
                }

                typeReference.replace(getFactory().Type().createReference(rewriteName(qualifiedName)));
                return;
            }

            String simpleName = reference.getSimpleName();
            if (simpleName != null && simpleName.startsWith(OLD_PREFIX)) {
                reference.replace(getFactory().Type().createReference(rewriteName(simpleName)));
            }
        }

        private String rewriteName(String name) {
            return NEW_PREFIX + name.substring(OLD_PREFIX.length());
        }
    }
}
