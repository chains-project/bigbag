package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtAnnotation;

import java.lang.annotation.Annotation;
import java.util.Objects;

public class Main {
    private static final String OLD_ANNOTATION = "javax.validation.Valid";
    private static final String NEW_ANNOTATION = "jakarta.validation.Valid";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <input-source-dir> [output-source-dir]");
        }

        final String inputSourceDir = args[0];
        final String outputSourceDir = args.length > 1 ? args[1] : inputSourceDir + "-fixed";

        final Launcher launcher = new Launcher();
        launcher.addInputResource(inputSourceDir);
        launcher.setSourceOutputDirectory(outputSourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addProcessor(new AbstractProcessor<CtAnnotation<? extends Annotation>>() {
            @Override
            public void process(CtAnnotation<? extends Annotation> annotation) {
                if (annotation.getAnnotationType() == null) {
                    return;
                }

                if (Objects.equals(annotation.getAnnotationType().getQualifiedName(), OLD_ANNOTATION)) {
                    annotation.setAnnotationType(
                            annotation.getFactory().Type().createReference(NEW_ANNOTATION));
                }
            }
        });
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }
}
