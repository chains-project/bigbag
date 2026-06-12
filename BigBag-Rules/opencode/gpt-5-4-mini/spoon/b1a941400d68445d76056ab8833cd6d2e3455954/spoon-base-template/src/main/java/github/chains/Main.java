package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: Main <input-source-dir> [output-dir]");
        }

        String inputSourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputSourceDir + "-spoon-out";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setSourceOutputDirectory(new File(outputDir));
        launcher.addInputResource(inputSourceDir);

        launcher.buildModel();
        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtTypeReference<?> type = call.getType();
            if (type == null) {
                continue;
            }

            String qualifiedName = type.getQualifiedName();
            if (!"org.yaml.snakeyaml.constructor.Constructor".equals(qualifiedName)
                    && !"org.yaml.snakeyaml.Yaml".equals(qualifiedName)) {
                continue;
            }

            if (!call.getArguments().isEmpty()) {
                continue;
            }

            Factory factory = call.getFactory();
            CtConstructorCall<?> replacement = factory.Code().createConstructorCall(
                    factory.Type().createReference(qualifiedName),
                    factory.Code().createCodeSnippetExpression(
                            "new org.yaml.snakeyaml.LoaderOptions() {{ setTagInspector(new org.yaml.snakeyaml.inspector.TrustedTagInspector()); }}"
                    )
            );
            call.replace(replacement);
        }

        launcher.prettyprint();
    }
}
