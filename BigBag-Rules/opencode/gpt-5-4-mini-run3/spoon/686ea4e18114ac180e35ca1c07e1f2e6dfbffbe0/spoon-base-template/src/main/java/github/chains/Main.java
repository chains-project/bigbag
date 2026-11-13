package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

    static {
        // Dropwizard 4 core package move.
        TYPE_RENAMES.put("io.dropwizard.Application", "io.dropwizard.core.Application");
        TYPE_RENAMES.put("io.dropwizard.Configuration", "io.dropwizard.core.Configuration");
        TYPE_RENAMES.put("io.dropwizard.ConfiguredBundle", "io.dropwizard.core.ConfiguredBundle");
        TYPE_RENAMES.put("io.dropwizard.cli.CheckCommand", "io.dropwizard.core.cli.CheckCommand");
        TYPE_RENAMES.put("io.dropwizard.cli.Cli", "io.dropwizard.core.cli.Cli");
        TYPE_RENAMES.put("io.dropwizard.cli.Command", "io.dropwizard.core.cli.Command");
        TYPE_RENAMES.put("io.dropwizard.cli.ConfiguredCommand", "io.dropwizard.core.cli.ConfiguredCommand");
        TYPE_RENAMES.put("io.dropwizard.cli.EnvironmentCommand", "io.dropwizard.core.cli.EnvironmentCommand");
        TYPE_RENAMES.put("io.dropwizard.cli.ServerCommand", "io.dropwizard.core.cli.ServerCommand");
        TYPE_RENAMES.put("io.dropwizard.server.AbstractServerFactory", "io.dropwizard.core.server.AbstractServerFactory");
        TYPE_RENAMES.put("io.dropwizard.server.DefaultServerFactory", "io.dropwizard.core.server.DefaultServerFactory");
        TYPE_RENAMES.put("io.dropwizard.server.ServerFactory", "io.dropwizard.core.server.ServerFactory");
        TYPE_RENAMES.put("io.dropwizard.server.SimpleServerFactory", "io.dropwizard.core.server.SimpleServerFactory");
        TYPE_RENAMES.put("io.dropwizard.setup.Bootstrap", "io.dropwizard.core.setup.Bootstrap");
        TYPE_RENAMES.put("io.dropwizard.setup.Environment", "io.dropwizard.core.setup.Environment");
        TYPE_RENAMES.put("io.dropwizard.validation.InjectValidatorFeature", "io.dropwizard.core.validation.InjectValidatorFeature");
        TYPE_RENAMES.put("io.dropwizard.sslreload.SslReloadBundle", "io.dropwizard.core.sslreload.SslReloadBundle");
        TYPE_RENAMES.put("io.dropwizard.sslreload.SslReloadTask", "io.dropwizard.core.sslreload.SslReloadTask");

        // Jakarta namespace migration required by Dropwizard 4.
        TYPE_RENAMES.put("javax.ws.rs", "jakarta.ws.rs");
        TYPE_RENAMES.put("javax.ws.rs.client", "jakarta.ws.rs.client");
        TYPE_RENAMES.put("javax.ws.rs.container", "jakarta.ws.rs.container");
        TYPE_RENAMES.put("javax.ws.rs.core", "jakarta.ws.rs.core");
        TYPE_RENAMES.put("javax.ws.rs.ext", "jakarta.ws.rs.ext");
        TYPE_RENAMES.put("javax.servlet", "jakarta.servlet");
        TYPE_RENAMES.put("javax.validation", "jakarta.validation");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1
                ? Paths.get(args[1])
                : inputDir.resolveSibling(inputDir.getFileName() + "-rewritten");

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public boolean isToBeProcessed(CtTypeReference<?> candidate) {
                return candidate != null && rewrite(candidate.getQualifiedName()) != null;
            }

            @Override
            public void process(CtTypeReference<?> reference) {
                String rewritten = rewrite(reference.getQualifiedName());
                if (rewritten != null && !rewritten.equals(reference.getQualifiedName())) {
                    CtTypeReference<?> replacement = getFactory().Type().createReference(rewritten);
                    reference.replace(replacement);
                }
            }
        });

        launcher.run();
    }

    private static String rewrite(String qualifiedName) {
        for (Map.Entry<String, String> entry : TYPE_RENAMES.entrySet()) {
            String oldPrefix = entry.getKey();
            String newPrefix = entry.getValue();
            if (qualifiedName.equals(oldPrefix)) {
                return newPrefix;
            }
            if (qualifiedName.startsWith(oldPrefix + ".")) {
                return newPrefix + qualifiedName.substring(oldPrefix.length());
            }
        }
        return null;
    }
}
