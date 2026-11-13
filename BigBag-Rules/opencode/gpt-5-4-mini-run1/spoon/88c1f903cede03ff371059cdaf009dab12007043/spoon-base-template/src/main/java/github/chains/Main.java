package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String OLD_ZIPFILE_TYPE = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_ZIPFILE_TYPE = "net.lingala.zip4j.ZipFile";

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDirectory = args[0];
        String outputDirectory = args.length == 2 ? args[1] : inputDirectory + "-transformed";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.setSourceOutputDirectory(new File(outputDirectory));
        launcher.addInputResource(inputDirectory);
        launcher.buildModel();

        applyZip4jZipFileRewrite(launcher);
    }

    private static void applyZip4jZipFileRewrite(Launcher launcher) {
        CtTypeReference<?> replacementType = launcher.getFactory().Type().createReference(NEW_ZIPFILE_TYPE);

        List<CtTypeReference<?>> typeReferences = new ArrayList<>(launcher.getModel()
                .getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> typeReference : typeReferences) {
            if (OLD_ZIPFILE_TYPE.equals(typeReference.getQualifiedName())) {
                typeReference.replace(replacementType.clone());
            }
        }

        List<CtExecutableReference<?>> executableReferences = new ArrayList<>(launcher.getModel()
                .getElements(new TypeFilter<>(CtExecutableReference.class)));
        for (CtExecutableReference<?> executableReference : executableReferences) {
            if (executableReference.getDeclaringType() != null
                    && OLD_ZIPFILE_TYPE.equals(executableReference.getDeclaringType().getQualifiedName())) {
                executableReference.setDeclaringType(replacementType.clone());
            }
        }

        rewriteSourceFiles(launcher, OLD_ZIPFILE_TYPE, NEW_ZIPFILE_TYPE);
    }

    private static void rewriteSourceFiles(Launcher launcher, String oldType, String newType) {
        List<Path> sourceFiles = new ArrayList<>();
        launcher.getModel().getAllTypes().forEach(type -> {
            if (type.getPosition() != null && type.getPosition().getFile() != null) {
                sourceFiles.add(type.getPosition().getFile().toPath());
            }
        });

        for (Path sourceFile : sourceFiles) {
            try {
                String source = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
                String rewritten = source.replace(oldType, newType);
                if (!source.equals(rewritten)) {
                    Files.write(sourceFile, rewritten.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot rewrite " + sourceFile, e);
            }
        }
    }
}
