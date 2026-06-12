package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet.";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet.";
    private static final String JETTY_SERVER = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        File inputDir = new File(args[0]);
        File outputDir = new File(args.length > 1 ? args[1] : "spoon-output");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        List<CtTypeReference<?>> references = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            String qualifiedName = reference.getQualifiedName();

            if (OLD_CONNECTOR.equals(qualifiedName)) {
                reference.replace(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
                continue;
            }

            if (qualifiedName != null && qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                reference.replace(launcher.getFactory().Type().createReference(
                        NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length())));
            }
        }

        for (CtConstructorCall<?> constructorCall : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtTypeReference<?> type = constructorCall.getType();
            if (type == null || !OLD_CONNECTOR.equals(type.getQualifiedName())) {
                continue;
            }

            String serverExpression = findServerExpression(constructorCall);
            if (serverExpression == null) {
                continue;
            }

            constructorCall.getArguments().clear();
            constructorCall.addArgument(launcher.getFactory().Code().createCodeSnippetExpression(serverExpression));
        }

        launcher.prettyprint();
    }

    private static String findServerExpression(CtElement context) {
        CtExecutable<?> executable = context.getParent(CtExecutable.class);
        if (executable != null) {
            for (CtParameter<?> parameter : executable.getParameters()) {
                if (isJettyServer(parameter.getType())) {
                    return parameter.getSimpleName();
                }
            }
        }

        CtBlock<?> block = context.getParent(CtBlock.class);
        if (block != null && context.getPosition() != null && context.getPosition().isValidPosition()) {
            int sourceStart = context.getPosition().getSourceStart();
            CtLocalVariable<?> bestMatch = null;

            for (CtLocalVariable<?> localVariable : block.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                if (!isJettyServer(localVariable.getType()) || localVariable.getPosition() == null ||
                        !localVariable.getPosition().isValidPosition()) {
                    continue;
                }

                int localStart = localVariable.getPosition().getSourceStart();
                if (localStart <= sourceStart && (bestMatch == null || localStart > bestMatch.getPosition().getSourceStart())) {
                    bestMatch = localVariable;
                }
            }

            if (bestMatch != null) {
                return bestMatch.getSimpleName();
            }
        }

        CtElement parent = context;
        while (parent != null) {
            if (!(parent instanceof CtClass<?> enclosingType)) {
                parent = parent.getParent();
                continue;
            }

            for (CtField<?> field : enclosingType.getFields()) {
                if (isJettyServer(field.getType())) {
                    return "this." + field.getSimpleName();
                }
            }

            break;
        }

        return null;
    }

    private static boolean isJettyServer(CtTypeReference<?> typeReference) {
        return typeReference != null && JETTY_SERVER.equals(typeReference.getQualifiedName());
    }
}
