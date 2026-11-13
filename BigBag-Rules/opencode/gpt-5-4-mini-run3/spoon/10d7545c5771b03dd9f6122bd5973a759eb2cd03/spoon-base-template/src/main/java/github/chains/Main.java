package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_TYPE = "io.dropwizard.setup.Environment";
    private static final String NEW_TYPE = "io.dropwizard.core.setup.Environment";

    public static void main(String[] args) {
        Path inputPath = Paths.get(args.length > 0 ? args[0] : "src/main/java");
        Path outputPath = Paths.get(args.length > 1 ? args[1] : "target/spooned-sources");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(inputPath.toString());
        launcher.setSourceOutputDirectory(outputPath.toFile());
        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public boolean isToBeProcessed(CtTypeReference<?> typeReference) {
                return typeReference != null && OLD_TYPE.equals(typeReference.getQualifiedName());
            }

            @Override
            public void process(CtTypeReference<?> typeReference) {
                CtElement replacement = typeReference.getFactory().Type().createReference(NEW_TYPE);
                typeReference.replace(replacement);
            }
        });
        launcher.run();
    }
}
