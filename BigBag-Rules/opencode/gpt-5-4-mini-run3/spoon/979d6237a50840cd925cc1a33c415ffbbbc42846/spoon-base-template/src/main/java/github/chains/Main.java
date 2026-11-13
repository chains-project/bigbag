package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final String OLD_TYPE = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_TYPE = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_PACKAGE = NEW_TYPE.substring(0, NEW_TYPE.lastIndexOf('.'));
    private static final String NEW_SIMPLE_NAME = NEW_TYPE.substring(NEW_TYPE.lastIndexOf('.') + 1);

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length == 2 ? args[1] : inputDir + "-spooned";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(new File(outputDir));

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> reference) {
                rewriteTypeReference(reference);
            }
        });
        launcher.addProcessor(new AbstractProcessor<spoon.reflect.declaration.CtImport>() {
            @Override
            public void process(spoon.reflect.declaration.CtImport imp) {
                if (imp.getReference() instanceof CtTypeReference<?>) {
                    CtTypeReference<?> reference = (CtTypeReference<?>) imp.getReference();
                    if (OLD_TYPE.equals(reference.getQualifiedName())) {
                        imp.delete();
                    }
                }
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtTypeAccess<?>>() {
            @Override
            public void process(CtTypeAccess<?> typeAccess) {
                rewriteTypeReference(typeAccess.getAccessedType());
            }
        });

        launcher.run();
        rewriteGeneratedSources(Paths.get(outputDir));
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference) {
        if (reference != null && OLD_TYPE.equals(reference.getQualifiedName())) {
            reference.setPackage(reference.getFactory().Package().createReference(NEW_PACKAGE));
            reference.setSimpleName(NEW_SIMPLE_NAME);
        }
    }

    private static void rewriteGeneratedSources(Path outputDir) {
        try {
            Files.walk(outputDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to post-process generated sources", e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String updated = content.replace(OLD_TYPE, NEW_TYPE);
            if (!content.equals(updated)) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }
}
