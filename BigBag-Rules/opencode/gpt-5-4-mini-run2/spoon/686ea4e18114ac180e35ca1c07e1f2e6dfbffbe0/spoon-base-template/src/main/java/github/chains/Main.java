package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final Map<String, String> RENAMES = buildRenames();

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <inputSourceDir> [outputDir]");
        }

        Path inputDir = Path.of(args[0]);
        Path outputDir = args.length > 1
                ? Path.of(args[1])
                : inputDir.resolveSibling(inputDir.getFileName() + "-spoon-fixed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toString());
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public boolean isToBeProcessed(CtImport ctImport) {
                return ctImport.getReference() instanceof CtTypeReference
                        && hasRename(((CtTypeReference<?>) ctImport.getReference()).getQualifiedName());
            }

            @Override
            public void process(CtImport ctImport) {
                if (ctImport.getReference() instanceof CtTypeReference) {
                    ctImport.setReference(renameTypeReference((CtTypeReference<?>) ctImport.getReference()));
                }
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public boolean isToBeProcessed(CtTypeReference<?> reference) {
                return hasRename(reference.getQualifiedName());
            }

            @Override
            public void process(CtTypeReference<?> reference) {
                CtTypeReference<?> replacement = renameTypeReference(reference);
                if (replacement == reference) {
                    return;
                }

                replacement.setActualTypeArguments(reference.getActualTypeArguments());
                reference.replace(replacement);
            }
        });
        launcher.run();
        copyAndRewriteInputSources(inputDir, outputDir);
    }

    private static boolean hasRename(String qualifiedName) {
        for (String oldName : RENAMES.keySet()) {
            if (matchesPrefix(qualifiedName, oldName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPrefix(String qualifiedName, String oldName) {
        return qualifiedName.equals(oldName) || qualifiedName.startsWith(oldName + ".") || qualifiedName.startsWith(oldName + "$");
    }

    private static CtTypeReference<?> renameTypeReference(CtTypeReference<?> reference) {
        String qualifiedName = reference.getQualifiedName();
        String newName = null;
        for (Map.Entry<String, String> entry : RENAMES.entrySet()) {
            if (matchesPrefix(qualifiedName, entry.getKey())) {
                newName = entry.getValue() + qualifiedName.substring(entry.getKey().length());
                break;
            }
        }

        if (newName == null) {
            return reference;
        }

        return reference.getFactory().Type().createReference(newName);
    }

    private static Map<String, String> buildRenames() {
        Map<String, String> renames = new LinkedHashMap<>();
        renames.put("io.dropwizard.Application", "io.dropwizard.core.Application");
        renames.put("io.dropwizard.ConfiguredBundle", "io.dropwizard.core.ConfiguredBundle");
        renames.put("io.dropwizard.Configuration", "io.dropwizard.core.Configuration");
        renames.put("io.dropwizard.cli.Cli", "io.dropwizard.core.cli.Cli");
        renames.put("io.dropwizard.cli.CheckCommand", "io.dropwizard.core.cli.CheckCommand");
        renames.put("io.dropwizard.cli.Command", "io.dropwizard.core.cli.Command");
        renames.put("io.dropwizard.cli.ConfiguredCommand", "io.dropwizard.core.cli.ConfiguredCommand");
        renames.put("io.dropwizard.cli.EnvironmentCommand", "io.dropwizard.core.cli.EnvironmentCommand");
        renames.put("io.dropwizard.cli.ServerCommand", "io.dropwizard.core.cli.ServerCommand");
        renames.put("io.dropwizard.server.AbstractServerFactory", "io.dropwizard.core.server.AbstractServerFactory");
        renames.put("io.dropwizard.server.DefaultServerFactory", "io.dropwizard.core.server.DefaultServerFactory");
        renames.put("io.dropwizard.server.ServerFactory", "io.dropwizard.core.server.ServerFactory");
        renames.put("io.dropwizard.server.SimpleServerFactory", "io.dropwizard.core.server.SimpleServerFactory");
        renames.put("io.dropwizard.setup.AdminEnvironment", "io.dropwizard.core.setup.AdminEnvironment");
        renames.put("io.dropwizard.setup.AdminFactory", "io.dropwizard.core.setup.AdminFactory");
        renames.put("io.dropwizard.setup.Bootstrap", "io.dropwizard.core.setup.Bootstrap");
        renames.put("io.dropwizard.setup.Environment", "io.dropwizard.core.setup.Environment");
        renames.put("io.dropwizard.setup.ExceptionMapperBinder", "io.dropwizard.core.setup.ExceptionMapperBinder");
        renames.put("io.dropwizard.setup.HealthCheckConfiguration", "io.dropwizard.core.setup.HealthCheckConfiguration");
        renames.put("io.dropwizard.sslreload.SslReloadBundle", "io.dropwizard.core.sslreload.SslReloadBundle");
        renames.put("io.dropwizard.sslreload.SslReloadTask", "io.dropwizard.core.sslreload.SslReloadTask");
        renames.put("io.dropwizard.validation.InjectValidatorFeature", "io.dropwizard.core.validation.InjectValidatorFeature");
        renames.put("javax.annotation.", "jakarta.annotation.");
        renames.put("javax.servlet.", "jakarta.servlet.");
        renames.put("javax.validation.", "jakarta.validation.");
        renames.put("javax.ws.rs.", "jakarta.ws.rs.");
        return renames;
    }

    private static void copyAndRewriteInputSources(Path inputDir, Path outputDir) {
        try (Stream<Path> paths = Files.walk(inputDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            Path relative = inputDir.relativize(path);
                            Path target = outputDir.resolve(relative);
                            Files.createDirectories(target.getParent());
                            String source = Files.readString(path);
                            String rewritten = source;
                            for (Map.Entry<String, String> entry : RENAMES.entrySet()) {
                                rewritten = rewritten.replace(entry.getKey(), entry.getValue());
                            }
                            Files.writeString(target, rewritten, StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
