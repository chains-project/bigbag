package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class Main {

    private static final List<TypeMapping> MAPPINGS = Arrays.asList(
            new TypeMapping(
                    "org.codehaus.plexus.util.xml.Xpp3Dom",
                    "org.codehaus.plexus.configuration.PlexusConfiguration",
                    "org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration")
    );

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        File inputDir = new File(args[0]);
        File outputDir = new File(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.buildModel();

        apply(launcher);

        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
    }

    private static void apply(Launcher launcher) {
        for (TypeMapping mapping : MAPPINGS) {
            for (CtConstructorCall<?> call : launcher.getFactory().getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
                CtTypeReference<?> type = call.getType();
                if (type != null && mapping.oldType.equals(type.getQualifiedName())) {
                    call.setType(launcher.getFactory().Type().createReference(mapping.constructorType));
                }
            }

            for (CtTypeReference<?> reference : launcher.getFactory().getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
                if (mapping.oldType.equals(reference.getQualifiedName()) && !(reference.getParent() instanceof CtConstructorCall<?>)) {
                    reference.replace(launcher.getFactory().Type().createReference(mapping.newType));
                }
            }
        }
    }

    private static final class TypeMapping {
        private final String oldType;
        private final String newType;
        private final String constructorType;

        private TypeMapping(String oldType, String newType, String constructorType) {
            this.oldType = oldType;
            this.newType = newType;
            this.constructorType = constructorType;
        }
    }
}
