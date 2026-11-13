package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String YAML = "org.yaml.snakeyaml.Yaml";
    private static final String LOADER_OPTIONS_EXPR =
            "new org.yaml.snakeyaml.LoaderOptions() {{ setTagInspector(new org.yaml.snakeyaml.inspector.TrustedTagInspector()); }}";
    private static final String LOADER_OPTIONS = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> call) {
                String qualifiedName = call.getType() == null ? "" : call.getType().getQualifiedName();
                if (OLD_CONSTRUCTOR.equals(qualifiedName) && call.getArguments().isEmpty()) {
                    call.replace(getFactory().Code().createCodeSnippetExpression(
                            "new org.yaml.snakeyaml.constructor.Constructor(" + LOADER_OPTIONS_EXPR + ")"));
                } else if (YAML.equals(qualifiedName) && call.getArguments().isEmpty()) {
                    call.replace(getFactory().Code().createCodeSnippetExpression(
                            "new org.yaml.snakeyaml.Yaml(" + LOADER_OPTIONS_EXPR + ")"));
                } else if (LOADER_OPTIONS.equals(qualifiedName) && call.getArguments().isEmpty()) {
                    call.replace(getFactory().Code().createCodeSnippetExpression(LOADER_OPTIONS_EXPR));
                }
            }
        });

        launcher.run();
    }
}
