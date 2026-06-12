package github.chains;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_TYPE = "org.yaml.snakeyaml.inspector.TrustedTagInspector";
    private static final String NEW_TYPE = "org.yaml.snakeyaml.inspector.TagInspector";
    private static final String TAG_TYPE = "org.yaml.snakeyaml.nodes.Tag";
    private static final String REPLACEMENT =
            "new org.yaml.snakeyaml.inspector.TagInspector() { "
                    + "public boolean isGlobalTagAllowed(org.yaml.snakeyaml.nodes.Tag tag) { return true; } }";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        Environment environment = launcher.getEnvironment();
        environment.setNoClasspath(true);
        environment.setAutoImports(true);
        environment.setCommentEnabled(true);
        environment.setComplianceLevel(11);

        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new TrustedTagInspectorImportReplacement());
        launcher.addProcessor(new TrustedTagInspectorInstantiationReplacement());
        launcher.run();
    }

    private static final class TrustedTagInspectorImportReplacement extends AbstractProcessor<CtImport> {
        @Override
        public boolean isToBeProcessed(CtImport ctImport) {
            return ctImport.getReference() instanceof CtTypeReference
                    && OLD_TYPE.equals(((CtTypeReference<?>) ctImport.getReference()).getQualifiedName());
        }

        @Override
        public void process(CtImport ctImport) {
            ctImport.delete();
        }
    }

    private static final class TrustedTagInspectorInstantiationReplacement extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> constructorCall) {
            CtTypeReference<?> ref = constructorCall.getType();
            return ref != null && OLD_TYPE.equals(ref.getQualifiedName());
        }

        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            constructorCall.replace(getFactory().Code().createCodeSnippetExpression(REPLACEMENT));
        }
    }
}
