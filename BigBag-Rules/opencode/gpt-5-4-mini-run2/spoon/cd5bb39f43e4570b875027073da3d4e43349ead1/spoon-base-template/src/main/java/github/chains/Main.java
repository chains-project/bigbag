package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_TYPE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_TYPE = "org.codehaus.plexus.configuration.PlexusConfiguration";
    private static final String NEW_IMPL = "org.codehaus.plexus.configuration.DefaultPlexusConfiguration";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        File sourceDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.addInputResource(sourceDir.getAbsolutePath());
        launcher.getEnvironment().setSourceOutputDirectory(outputDir);

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public boolean isToBeProcessed(CtTypeReference<?> typeReference) {
                return OLD_TYPE.equals(typeReference.getQualifiedName());
            }

            @Override
            public void process(CtTypeReference<?> typeReference) {
                String replacement = typeReference.getParent(CtConstructorCall.class) != null
                        ? NEW_IMPL
                        : NEW_TYPE;
                typeReference.replace(typeReference.getFactory().Type().createReference(replacement));
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public boolean isToBeProcessed(CtConstructorCall<?> constructorCall) {
                CtTypeReference<?> type = constructorCall.getType();
                return type != null && OLD_TYPE.equals(type.getQualifiedName());
            }

            @Override
            public void process(CtConstructorCall<?> constructorCall) {
                constructorCall.setType(constructorCall.getFactory().Type().createReference(NEW_IMPL));
                constructorCall.getExecutable().setDeclaringType(constructorCall.getFactory().Type().createReference(NEW_IMPL));
            }
        });

        launcher.run();
    }
}
