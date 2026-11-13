package github.chains;

import java.nio.file.Path;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;

public class Main {
    private static final String OLD_TYPE = "org.yaml.snakeyaml.inspector.TrustedTagInspector";
    private static final String NEW_TYPE = "org.yaml.snakeyaml.inspector.UnTrustedTagInspector";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-dir>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(Path.of(args[1]).toFile());

        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> call) {
                if (call.getType() == null || !OLD_TYPE.equals(call.getType().getQualifiedName())) {
                    return;
                }

                call.replace(getFactory().Code().createCodeSnippetExpression(
                        "new org.yaml.snakeyaml.inspector.TagInspector() {"
                                + " public boolean isGlobalTagAllowed(org.yaml.snakeyaml.nodes.Tag tag) { return true; }"
                                + " }"));
            }
        });

        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }
}
