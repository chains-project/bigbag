package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_JWS_PREFIX = "javax.jws";
    private static final String OLD_JWS_SOAP_PREFIX = "javax.jws.soap";
    private static final String NEW_JWS_PREFIX = "jakarta.jws";
    private static final String NEW_JWS_SOAP_PREFIX = "jakarta.jws.soap";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length == 2 ? args[1] : args[0] + "-rewritten";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.addProcessor(new JwsTypeProcessor());
        launcher.addProcessor(new JwsImportProcessor());
        launcher.run();
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        if (qualifiedName.startsWith(OLD_JWS_SOAP_PREFIX)) {
            return NEW_JWS_SOAP_PREFIX + qualifiedName.substring(OLD_JWS_SOAP_PREFIX.length());
        }
        if (qualifiedName.startsWith(OLD_JWS_PREFIX)) {
            return NEW_JWS_PREFIX + qualifiedName.substring(OLD_JWS_PREFIX.length());
        }
        return qualifiedName;
    }

    private static boolean isJwsTypeName(String qualifiedName) {
        return qualifiedName != null
                && (qualifiedName.startsWith(OLD_JWS_SOAP_PREFIX) || qualifiedName.startsWith(OLD_JWS_PREFIX));
    }

    private static boolean isJwsReference(CtReference reference) {
        if (reference instanceof CtTypeReference<?>) {
            return isJwsTypeName(((CtTypeReference<?>) reference).getQualifiedName());
        }
        if (reference instanceof CtPackageReference) {
            return isJwsTypeName(((CtPackageReference) reference).getQualifiedName());
        }
        return false;
    }

    private static final class JwsTypeProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> reference) {
            return isJwsTypeName(reference.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> reference) {
            String rewritten = rewriteQualifiedName(reference.getQualifiedName());
            if (rewritten.equals(reference.getQualifiedName())) {
                return;
            }

            if (reference.getPackage() != null) {
                CtPackageReference packageReference = getFactory().Package().createReference(
                        reference.getPackage().getQualifiedName().startsWith(OLD_JWS_SOAP_PREFIX)
                                ? NEW_JWS_SOAP_PREFIX
                                : NEW_JWS_PREFIX);
                reference.setPackage(packageReference);
            }

            reference.setSimpleName(rewritten.substring(rewritten.lastIndexOf('.') + 1));
        }
    }

    private static final class JwsImportProcessor extends AbstractProcessor<CtImport> {
        @Override
        public boolean isToBeProcessed(CtImport importRef) {
            CtReference reference = importRef.getReference();
            return reference != null && isJwsReference(reference);
        }

        @Override
        public void process(CtImport importRef) {
            CtReference reference = importRef.getReference();
            if (reference instanceof CtTypeReference<?>) {
                String rewritten = rewriteQualifiedName(((CtTypeReference<?>) reference).getQualifiedName());
                importRef.setReference(getFactory().Type().createReference(rewritten));
            } else if (reference instanceof CtPackageReference) {
                String rewritten = rewriteQualifiedName(((CtPackageReference) reference).getQualifiedName());
                importRef.setReference(getFactory().Package().createReference(rewritten));
            }
        }
    }
}
