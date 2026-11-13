package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_TYPE = "org.yaml.snakeyaml.inspector.TrustedTagInspector";
    private static final String NEW_TYPE = "org.yaml.snakeyaml.inspector.UnTrustedTagInspector";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-dir>");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        List<CtImport> imports = new ArrayList<>(launcher.getModel().getElements(
                imp -> imp instanceof CtImport
                        && ((CtImport) imp).getReference() instanceof CtTypeReference<?> typeRef
                        && OLD_TYPE.equals(typeRef.getQualifiedName())));
        for (CtImport ctImport : imports) {
            ctImport.delete();
        }

        List<CtConstructorCall<?>> constructorCalls = new ArrayList<>(launcher.getModel().getElements(
                call -> call instanceof CtConstructorCall<?> && OLD_TYPE.equals(((CtConstructorCall<?>) call).getType().getQualifiedName())));
        for (CtConstructorCall<?> call : constructorCalls) {
            call.replace(launcher.getFactory().Code().createCodeSnippetExpression(
                    "new org.yaml.snakeyaml.inspector.UnTrustedTagInspector()"));
        }

        launcher.prettyprint();
    }
}
