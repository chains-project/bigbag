package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected at least the source directory path");
        }

        final String inputDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : inputDir + "-fixed";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.addInputResource(inputDir);
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        final List<CtConstructorCall<?>> constructorCalls = new ArrayList<>(
                launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));
        for (CtConstructorCall<?> call : constructorCalls) {
            if (OLD_CONNECTOR.equals(call.getType().getQualifiedName())) {
                CtExpression<?> serverExpression = findServerExpression(call, factory);
                if (serverExpression != null) {
                    CtTypeReference<?> serverConnectorType = factory.Type().createReference(NEW_CONNECTOR);
                    CtConstructorCall<?> replacement = factory.Code().createConstructorCall(
                            serverConnectorType,
                            serverExpression);
                    call.replace(replacement);
                } else {
                    call.getType().replace(factory.Type().createReference(NEW_CONNECTOR));
                }
            }
        }

        final List<CtTypeReference<?>> typeReferences = new ArrayList<>(
                launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));

        for (CtTypeReference<?> reference : typeReferences) {
            final String qualifiedName = reference.getQualifiedName();
            if (OLD_CONNECTOR.equals(qualifiedName)) {
                reference.replace(factory.Type().createReference(NEW_CONNECTOR));
            } else if (qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                reference.replace(factory.Type().createReference(
                        NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length())));
            }
        }

        final List<CtImport> imports = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtImport.class)));
        for (CtImport anImport : imports) {
            final CtReference reference = anImport.getReference();
            if (reference instanceof CtTypeReference) {
                final String qualifiedName = ((CtTypeReference<?>) reference).getQualifiedName();
                if (qualifiedName.equals(OLD_CONNECTOR) || qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                    anImport.delete();
                }
            }
        }

        launcher.prettyprint();
    }

    private static CtExpression<?> findServerExpression(CtElement element, Factory factory) {
        CtExecutable<?> executable = element.getParent(CtExecutable.class);
        if (executable != null && executable.getBody() != null) {
            for (CtLocalVariable<?> localVariable : executable.getBody().getElements(new TypeFilter<>(CtLocalVariable.class))) {
                if (isServerType(localVariable.getType())) {
                    return factory.Code().createVariableRead(localVariable.getReference(), false);
                }
            }
        }

        CtType<?> type = element.getParent(CtType.class);
        if (type != null) {
            for (CtField<?> field : type.getFields()) {
                if (isServerType(field.getType())) {
                    return factory.Code().createVariableRead(field.getReference(), field.hasModifier(ModifierKind.STATIC));
                }
            }
        }

        return null;
    }

    private static boolean isServerType(CtTypeReference<?> typeReference) {
        return typeReference != null && SERVER_TYPE.equals(typeReference.getQualifiedName());
    }
}
