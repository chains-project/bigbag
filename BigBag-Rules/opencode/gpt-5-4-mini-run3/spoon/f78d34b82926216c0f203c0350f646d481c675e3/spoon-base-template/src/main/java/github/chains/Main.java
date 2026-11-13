package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_PACKAGE = "develop.p2p.lib";
    private static final String NEW_PACKAGE = "tokyo.peya.lib";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <sourceDir> [outputDir]");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args.length > 1 ? args[1] : args[0]);
        launcher.addProcessor(new TypeProcessor());
        launcher.addProcessor(new ImportProcessor());
        launcher.run();
        launcher.prettyprint();
        rewriteSourceText(args.length > 1 ? args[1] : args[0]);
    }

    private static boolean hasOldPackage(String qualifiedName) {
        return qualifiedName != null && qualifiedName.startsWith(OLD_PACKAGE);
    }

    private static void rewriteSourceText(String root) {
        try {
            Files.walk(Paths.get(root))
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String updated = content.replace(OLD_PACKAGE, NEW_PACKAGE);
            if (!content.equals(updated)) {
                Files.writeString(path, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        return NEW_PACKAGE + qualifiedName.substring(OLD_PACKAGE.length());
    }

    private static void rewriteTypeReference(CtTypeReference reference) {
        String qualifiedName = reference.getQualifiedName();
        if (!hasOldPackage(qualifiedName)) {
            return;
        }
        String rewritten = rewriteQualifiedName(qualifiedName);
        int lastDot = rewritten.lastIndexOf('.');
        reference.setPackage(reference.getFactory().Package().createReference(rewritten.substring(0, lastDot)));
        reference.setSimpleName(rewritten.substring(lastDot + 1));
    }

    private static final class TypeProcessor extends AbstractProcessor<CtTypeReference> {
        @Override
        public boolean isToBeProcessed(CtTypeReference candidate) {
            return candidate != null && hasOldPackage(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference reference) {
            rewriteTypeReference(reference);
        }
    }

    private static final class ImportProcessor extends AbstractProcessor<CtImport> {
        @Override
        public boolean isToBeProcessed(CtImport candidate) {
            return candidate != null && candidate.getReference() != null;
        }

        @Override
        public void process(CtImport ctImport) {
            CtReference reference = ctImport.getReference();

            if (reference instanceof CtTypeReference) {
                CtTypeReference typeReference = (CtTypeReference) reference;
                if (hasOldPackage(typeReference.getQualifiedName())) {
                    rewriteTypeReference(typeReference);
                    ctImport.setReference(typeReference);
                }
                return;
            }

            if (reference instanceof CtExecutableReference) {
                CtExecutableReference executableReference = (CtExecutableReference) reference;
                CtTypeReference declaringType = executableReference.getDeclaringType();
                if (declaringType != null && hasOldPackage(declaringType.getQualifiedName())) {
                    rewriteTypeReference(declaringType);
                    executableReference.setDeclaringType(declaringType);
                    ctImport.setReference(executableReference);
                }
                return;
            }

            if (reference instanceof CtFieldReference) {
                CtFieldReference fieldReference = (CtFieldReference) reference;
                CtTypeReference declaringType = fieldReference.getDeclaringType();
                if (declaringType != null && hasOldPackage(declaringType.getQualifiedName())) {
                    rewriteTypeReference(declaringType);
                    fieldReference.setDeclaringType(declaringType);
                    ctImport.setReference(fieldReference);
                }
            }
        }
    }
}
