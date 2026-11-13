package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String REMOVED_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String REMOVED_METHOD = "getJavaScriptResult";

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <inputDir> [outputDir]");
        }

        Path inputDirectory = Paths.get(args[0]);
        Path outputDirectory = args.length > 1 ? Paths.get(args[1]) : inputDirectory;

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDirectory.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(outputDirectory.toString());
        launcher.buildModel();

        rewriteScriptResultUsage(launcher);
        launcher.prettyprint();
    }

    private static void rewriteScriptResultUsage(final Launcher launcher) {
        List<CtImport> imports = launcher.getModel().getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            if (ctImport.getReference() instanceof CtTypeReference<?> typeReference
                    && REMOVED_TYPE.equals(typeReference.getQualifiedName())) {
                ctImport.delete();
            }
        }

        List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!REMOVED_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtConstructorCall<?> constructorCall)) {
                continue;
            }

            if (!REMOVED_TYPE.equals(constructorCall.getType().getQualifiedName())) {
                continue;
            }

            if (constructorCall.getArguments().isEmpty()) {
                continue;
            }

            invocation.replace(constructorCall.getArguments().get(0).clone());
        }
    }
}
