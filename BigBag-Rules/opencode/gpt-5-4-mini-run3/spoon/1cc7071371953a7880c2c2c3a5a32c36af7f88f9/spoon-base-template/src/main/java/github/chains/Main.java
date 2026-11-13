package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Paths;

public class Main {
    private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static final String NEW_PREFIX = "net.bytebuddy";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(Paths.get(args[1]).toFile());
        launcher.addProcessor(new ByteBuddyTypeReferenceProcessor());
        launcher.addProcessor(new ByteBuddyImportProcessor());
        launcher.run();
    }

    private static final class ByteBuddyTypeReferenceProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> reference) {
            String qualifiedName = reference.getQualifiedName();
            if (qualifiedName.startsWith(OLD_PREFIX)) {
                reference.replace(reference.getFactory().Type().createReference(rewritePrefix(qualifiedName)));
            }
        }

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            return candidate != null && candidate.getQualifiedName().startsWith(OLD_PREFIX);
        }
    }

    private static final class ByteBuddyImportProcessor extends AbstractProcessor<CtImport> {
        @Override
        public void process(CtImport ctImport) {
            CtReference reference = ctImport.getReference();
            if (reference instanceof CtTypeReference) {
                CtTypeReference<?> typeReference = (CtTypeReference<?>) reference;
                String qualifiedName = typeReference.getQualifiedName();
                if (qualifiedName.startsWith(OLD_PREFIX)) {
                    ctImport.setReference(typeReference.getFactory().Type().createReference(rewritePrefix(qualifiedName)));
                }
            }
        }

        @Override
        public boolean isToBeProcessed(CtImport candidate) {
            CtReference reference = candidate == null ? null : candidate.getReference();
            return reference instanceof CtTypeReference && ((CtTypeReference<?>) reference).getQualifiedName().startsWith(OLD_PREFIX);
        }
    }

    private static String rewritePrefix(String qualifiedName) {
        return NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
    }
}
