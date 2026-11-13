package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    private static final String OLD_SESSION_IMPLEMENTOR = "org.hibernate.engine.spi.SessionImplementor";
    private static final String NEW_SESSION_CONTRACT_IMPLEMENTOR =
            "org.hibernate.engine.spi.SharedSessionContractImplementor";
    private static final String OLD_SERVICE_REGISTRY_BUILDER =
            "org.hibernate.service.ServiceRegistryBuilder";
    private static final String NEW_SERVICE_REGISTRY_BUILDER =
            "org.hibernate.boot.registry.StandardServiceRegistryBuilder";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-dir]");
            System.exit(1);
        }

        String inputDir = args[0];
        String outputDir = args.length == 2 ? args[1] : inputDir + "-spooned";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new HibernateTypeMigrationProcessor());
        launcher.run();
        rewriteGeneratedSources(outputDir);
    }

    private static void rewriteGeneratedSources(String outputDir) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("org.hibernate.engine.spi.SessionImplementor", NEW_SESSION_CONTRACT_IMPLEMENTOR);
        replacements.put("org.hibernate.service.ServiceRegistryBuilder", NEW_SERVICE_REGISTRY_BUILDER);
        replacements.put("new ServiceRegistryBuilder()", "new StandardServiceRegistryBuilder()");
        replacements.put(".buildServiceRegistry()", ".build()");
        replacements.put("SessionImplementor", "SharedSessionContractImplementor");

        try {
            Files.walk(Paths.get(outputDir))
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> applyReplacements(path, replacements));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to rewrite generated sources", ex);
        }
    }

    private static void applyReplacements(Path path, Map<String, String> replacements) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String updated = content;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                updated = updated.replace(entry.getKey(), entry.getValue());
            }
            if (!content.equals(updated)) {
                Files.writeString(path, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to rewrite " + path, ex);
        }
    }

    private static final class HibernateTypeMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> typeReference) {
            String qualifiedName = typeReference.getQualifiedName();
            return OLD_SESSION_IMPLEMENTOR.equals(qualifiedName)
                    || OLD_SERVICE_REGISTRY_BUILDER.equals(qualifiedName);
        }

        @Override
        public void process(CtTypeReference<?> typeReference) {
            String qualifiedName = typeReference.getQualifiedName();
            String replacementName = null;
            if (OLD_SESSION_IMPLEMENTOR.equals(qualifiedName)) {
                replacementName = NEW_SESSION_CONTRACT_IMPLEMENTOR;
            } else if (OLD_SERVICE_REGISTRY_BUILDER.equals(qualifiedName)) {
                replacementName = NEW_SERVICE_REGISTRY_BUILDER;
            }
            if (replacementName == null) {
                return;
            }

            CtTypeReference<?> replacement = typeReference.getFactory().Type().createReference(replacementName);
            typeReference.replace(replacement);
        }
    }
}
