package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String JETTY_SERVER = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        transformJettyConnectorConstruction(launcher);
        transformImports(launcher);
        transformServletTypes(launcher);

        launcher.prettyprint();
        postProcessWrittenSources(args[1]);
    }

    private static void transformImports(Launcher launcher) {
        List<CtCompilationUnit> compilationUnits = launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class));
        for (CtCompilationUnit compilationUnit : compilationUnits) {
            Collection<CtImport> currentImports = compilationUnit.getImports();
            if (currentImports == null || currentImports.isEmpty()) {
                continue;
            }

            Collection<CtImport> updatedImports = new ArrayList<>();
            for (CtImport ctImport : currentImports) {
                CtTypeReference<?> reference = (CtTypeReference<?>) ctImport.getReference();
                if (reference == null) {
                    updatedImports.add(ctImport);
                    continue;
                }

                String qualifiedName = reference.getQualifiedName();
                if (OLD_CONNECTOR.equals(qualifiedName)) {
                    updatedImports.add(launcher.getFactory().Type().createImport(
                            launcher.getFactory().Type().createReference(NEW_CONNECTOR)));
                } else if (qualifiedName != null && qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                    updatedImports.add(launcher.getFactory().Type().createImport(
                            launcher.getFactory().Type().createReference(
                                    NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length()))));
                } else {
                    updatedImports.add(ctImport);
                }
            }

            currentImports.clear();
            currentImports.addAll(updatedImports);
        }
    }

    private static void transformJettyConnectorConstruction(Launcher launcher) {
        List<CtConstructorCall> constructorCalls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall call : constructorCalls) {
            CtTypeReference type = call.getType();
            if (type == null || !OLD_CONNECTOR.equals(type.getQualifiedName())) {
                continue;
            }

            CtExpression serverExpression = findServerExpression(call);
            if (serverExpression == null) {
                continue;
            }

            CtConstructorCall replacement = launcher.getFactory().Code().createConstructorCall(
                    launcher.getFactory().Type().createReference(NEW_CONNECTOR),
                    serverExpression.clone());
            call.replace(replacement);
        }
    }

    private static CtExpression findServerExpression(CtConstructorCall call) {
        CtExecutable executable = call.getParent(CtExecutable.class);
        if (executable != null) {
            for (Object parameterObject : executable.getParameters()) {
                CtParameter parameter = (CtParameter) parameterObject;
                if (isJettyServer(parameter)) {
                    return parameter.getFactory().Code().createVariableRead(parameter.getReference(), false);
                }
            }
        }

        CtType type = call.getParent(CtType.class);
        if (type != null) {
            for (Object fieldObject : type.getFields()) {
                CtField field = (CtField) fieldObject;
                if (isJettyServer(field)) {
                    return field.getFactory().Code().createVariableRead(field.getReference(), false);
                }
            }
        }

        return null;
    }

    private static boolean isJettyServer(CtVariable variable) {
        CtTypeReference reference = variable.getType();
        return reference != null && JETTY_SERVER.equals(reference.getQualifiedName());
    }

    private static void transformServletTypes(Launcher launcher) {
        List<CtTypeReference> references = launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference reference : references) {
            String qualifiedName = reference.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                reference.replace(launcher.getFactory().Type().createReference(
                        NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length())));
            }
        }
    }

    private static void postProcessWrittenSources(String outputDir) {
        Path root = Paths.get(outputDir);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteSourceFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteSourceFile(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> rewritten = new ArrayList<>();
            java.util.LinkedHashSet<String> imports = new java.util.LinkedHashSet<>();
            boolean inImportBlock = false;

            for (String line : lines) {
                if (line.startsWith("import ")) {
                    inImportBlock = true;
                    if (line.contains("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                        continue;
                    }
                    line = line.replace("javax.servlet", NEW_SERVLET_PREFIX);
                    imports.add(line);
                    continue;
                }

                if (inImportBlock) {
                    rewritten.addAll(imports);
                    imports.clear();
                    inImportBlock = false;
                }

                rewritten.add(line.replace("org.eclipse.jetty.server.nio.SelectChannelConnector", NEW_CONNECTOR));
            }

            if (inImportBlock) {
                rewritten.addAll(imports);
            }

            Files.write(path, rewritten, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
