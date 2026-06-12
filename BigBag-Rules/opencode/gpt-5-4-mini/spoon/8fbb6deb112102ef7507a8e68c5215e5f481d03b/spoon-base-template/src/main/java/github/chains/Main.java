package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_SERVLET = "javax.servlet";
    private static final String NEW_SERVLET = "jakarta.servlet";
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.buildModel();

        transformServletPackageRenames(launcher);
        transformConnectorConstruction(launcher);

        launcher.prettyprint();
        rewriteGeneratedSources(args[1]);
    }

    private static void transformServletPackageRenames(Launcher launcher) {
        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String qualifiedName = reference.getQualifiedName();
            if (qualifiedName.startsWith(OLD_SERVLET)) {
                reference.replace(launcher.getFactory().Type().createReference(qualifiedName.replace(OLD_SERVLET, NEW_SERVLET)));
            }
        }
    }

    private static void transformConnectorConstruction(Launcher launcher) {
        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtTypeReference<?> targetType = call.getType();
            if (targetType == null || !OLD_CONNECTOR.equals(targetType.getQualifiedName())) {
                continue;
            }

            CtExpression<?> serverExpression = findServerExpression(call);
            if (serverExpression == null) {
                continue;
            }

            CtConstructorCall<?> replacement = launcher.getFactory().Core().createConstructorCall();
            replacement.setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
            replacement.addArgument(serverExpression.clone());
            call.replace(replacement);
        }
    }

    private static void rewriteGeneratedSources(String outputDir) {
        try {
            List<Path> javaFiles = Files.walk(new File(outputDir).toPath())
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                String content = new String(Files.readAllBytes(javaFile), StandardCharsets.UTF_8);
                if (content.contains("toByteArray(") && !content.contains("import static org.apache.commons.io.IOUtils.toByteArray;")) {
                    content = content.replaceFirst("package [^;]+;\\s*", "$0\\nimport static org.apache.commons.io.IOUtils.toByteArray;\\n");
                }
                Files.write(javaFile, content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static CtExpression<?> findServerExpression(CtConstructorCall<?> call) {
        CtExecutable<?> executable = call.getParent(CtExecutable.class);
        if (executable == null || executable.getBody() == null) {
            return null;
        }

        CtExpression<?> preferred = null;
        List<CtVariableAccess> accesses = executable.getBody().getElements(new TypeFilter<>(CtVariableAccess.class));
        for (CtVariableAccess<?> access : accesses) {
            CtTypeReference<?> type = access.getVariable() != null ? access.getVariable().getType() : null;
            if (type != null && SERVER.equals(type.getQualifiedName()) && preferred == null) {
                preferred = access;
            }
        }

        if (preferred != null) {
            return preferred;
        }

        List<CtLocalVariable> locals = executable.getBody().getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable<?> local : locals) {
            CtTypeReference<?> type = local.getType();
            if (type != null && SERVER.equals(type.getQualifiedName()) && local.getDefaultExpression() != null) {
                return local.getDefaultExpression();
            }
        }

        List<CtFieldRead> fieldReads = executable.getBody().getElements(new TypeFilter<>(CtFieldRead.class));
        for (CtFieldRead<?> fieldRead : fieldReads) {
            CtTypeReference<?> type = fieldRead.getVariable() != null ? fieldRead.getVariable().getType() : null;
            if (type != null && SERVER.equals(type.getQualifiedName())) {
                return fieldRead;
            }
        }

        return null;
    }
}
