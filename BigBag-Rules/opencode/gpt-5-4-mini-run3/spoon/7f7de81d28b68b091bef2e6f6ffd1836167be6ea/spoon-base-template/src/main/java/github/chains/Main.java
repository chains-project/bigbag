package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class Main {

    private static final String TARGET_TYPE = "org.snmp4j.agent.ManagedObject";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected: <input-source-dir> <output-source-dir>");
        }

        File inputDir = new File(args[0]);
        File outputDir = new File(args[1]);

        File projectRoot = locateProjectRoot(inputDir);
        String[] classpath = buildClasspath(projectRoot);

        spoon.Launcher launcher = new spoon.Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.getEnvironment().setSourceClasspath(classpath);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new RawManagedObjectToWildcardProcessor());
        launcher.run();
    }

    private static class RawManagedObjectToWildcardProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public void process(CtTypeReference<?> reference) {
            if (!TARGET_TYPE.equals(reference.getQualifiedName())) {
                return;
            }
            if (!reference.getActualTypeArguments().isEmpty()) {
                return;
            }

            CtWildcardReference wildcard = getFactory().createWildcardReference();
            reference.setActualTypeArguments(Collections.singletonList(wildcard));
        }
    }

    private static File locateProjectRoot(File inputDir) {
        File current = inputDir.isDirectory() ? inputDir : inputDir.getParentFile();
        while (current != null) {
            File pom = new File(current, "pom.xml");
            if (pom.isFile()) {
                return current;
            }
            current = current.getParentFile();
        }
        return inputDir;
    }

    private static String[] buildClasspath(File projectRoot) {
        try {
            Path cpFile = Files.createTempFile("spoon-classpath", ".txt");
            Process process = new ProcessBuilder(
                    "mvn", "-q", "-DskipTests",
                    "dependency:build-classpath", "-Dmdep.outputFile=" + cpFile.toAbsolutePath()
            ).directory(projectRoot).inheritIO().start();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Could not build project classpath");
            }
            String cp = Files.readString(cpFile, StandardCharsets.UTF_8).trim();
            return cp.isEmpty() ? new String[0] : cp.split(File.pathSeparator);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Could not determine project classpath", e);
        }
    }
}
