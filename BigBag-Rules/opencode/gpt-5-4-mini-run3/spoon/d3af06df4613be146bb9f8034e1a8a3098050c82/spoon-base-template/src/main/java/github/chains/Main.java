package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.List;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String OLD_SERVLET_GROUP = "org.eclipse.jetty.orbit";
    private static final String OLD_SERVLET_ARTIFACT = "javax.servlet";
    private static final String NEW_SERVLET_GROUP = "jakarta.servlet";
    private static final String NEW_SERVLET_ARTIFACT = "jakarta.servlet-api";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input.resolveSibling(input.getFileName() + "-transformed");

        Files.createDirectories(output);
        copyProjectFiles(input, output);

        Launcher launcher = new Launcher();
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();

        transform(launcher);

        launcher.prettyprint();
        transformPomFiles(output);
        rewriteJavaText(output);
    }

    private static void transform(Launcher launcher) {
        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtTypeReference<?> type = call.getType();
            if (type != null && OLD_CONNECTOR.equals(type.getQualifiedName())) {
                CtExpression<?> serverExpression = resolveServerExpression(call, launcher);
                if (serverExpression != null) {
                    CtConstructorCall<?> replacement = launcher.getFactory().Core().createConstructorCall();
                    replacement.setType(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
                    replacement.addArgument(serverExpression.clone());
                    call.replace(replacement);
                }
            }
        }

        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String qualifiedName = reference.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                reference.replace(launcher.getFactory().Type().createReference(
                        NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length())));
            }
        }

        for (CtImport importDecl : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (importDecl.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> reference = (CtTypeReference<?>) importDecl.getReference();
                String qualifiedName = reference.getQualifiedName();
                if (OLD_CONNECTOR.equals(qualifiedName)) {
                    importDecl.setReference(launcher.getFactory().Type().createReference(NEW_CONNECTOR));
                } else if (qualifiedName != null && qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                    importDecl.setReference(launcher.getFactory().Type().createReference(
                            NEW_SERVLET_PREFIX + qualifiedName.substring(OLD_SERVLET_PREFIX.length())));
                }
            }
        }
    }

    private static CtExpression<?> resolveServerExpression(CtElement element, Launcher launcher) {
        CtConstructor<?> constructor = element.getParent(CtConstructor.class);
        if (constructor != null) {
            CtExpression<?> fromParameter = findServerReference(constructor.getParameters(), launcher);
            if (fromParameter != null) {
                return fromParameter;
            }
        }

        CtMethod<?> method = element.getParent(CtMethod.class);
        if (method != null) {
            CtExpression<?> fromParameter = findServerReference(method.getParameters(), launcher);
            if (fromParameter != null) {
                return fromParameter;
            }
        }

        CtType<?> type = element.getParent(CtType.class);
        if (type != null) {
            for (CtField<?> field : type.getFields()) {
                if (field.getType() != null && SERVER_TYPE.equals(field.getType().getQualifiedName())) {
                    CtVariableReference<?> reference = field.getReference();
                    return launcher.getFactory().createVariableRead(reference, false);
                }
            }
        }

        return null;
    }

    private static CtExpression<?> findServerReference(Iterable<? extends CtElement> elements, Launcher launcher) {
        for (CtElement element : elements) {
            if (element instanceof CtParameter) {
                CtParameter<?> parameter = (CtParameter<?>) element;
                if (parameter.getType() != null && SERVER_TYPE.equals(parameter.getType().getQualifiedName())) {
                    return launcher.getFactory().createVariableRead(parameter.getReference(), false);
                }
            }
        }
        return null;
    }

    private static void copyProjectFiles(Path input, Path output) throws Exception {
        try (Stream<Path> stream = Files.walk(input)) {
            stream.filter(path -> !path.equals(input))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .forEach(path -> {
                        try {
                            Path relative = input.relativize(path);
                            Path destination = output.resolve(relative.toString());
                            if (Files.isDirectory(path)) {
                                Files.createDirectories(destination);
                            } else if (path.getFileName().toString().endsWith(".xml") || path.getFileName().toString().equals("pom.xml")) {
                                Files.createDirectories(destination.getParent());
                                String content = Files.readString(path);
                                Files.writeString(destination, rewritePom(content), StandardCharsets.UTF_8);
                            } else if (!path.getFileName().toString().endsWith(".java")) {
                                Files.createDirectories(destination.getParent());
                                Files.copy(path, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } else {
                                Files.createDirectories(destination.getParent());
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static void transformPomFiles(Path root) throws Exception {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.getFileName() != null && path.getFileName().toString().equals("pom.xml"))
                    .forEach(path -> {
                        try {
                            Files.writeString(path, rewritePom(Files.readString(path)), StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static String rewritePom(String content) {
        String rewritten = content.replace("<groupId>org.eclipse.jetty.orbit</groupId>", "<groupId>jakarta.servlet</groupId>")
                .replace("<artifactId>javax.servlet</artifactId>", "<artifactId>jakarta.servlet-api</artifactId>")
                .replace("<version>3.0.0.v201112011016</version>", "<version>5.0.0</version>");
        rewritten = rewritten.replace("<source>1.6</source>", "<source>1.8</source>");
        rewritten = rewritten.replace("<target>1.6</target>", "<target>1.8</target>");
        return rewritten;
    }

    private static void rewriteJavaText(Path root) throws Exception {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String rewritten = content.replace("import org.eclipse.jetty.server.nio.SelectChannelConnector;\n", "")
                                    .replace("javax.servlet", "jakarta.servlet");
                            if (!rewritten.equals(content)) {
                                Files.writeString(path, rewritten, StandardCharsets.UTF_8);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
