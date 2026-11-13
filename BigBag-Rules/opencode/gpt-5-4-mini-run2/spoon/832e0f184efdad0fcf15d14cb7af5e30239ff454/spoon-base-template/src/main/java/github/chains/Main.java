package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_TYPE = "com.cryptomorin.xseries.XEnchantment";
    private static final String OLD_METHOD = "parseEnchantment";
    private static final String NEW_METHOD = "getEnchant";
    private static final String NEW_RETURN_TYPE = "org.bukkit.enchantments.Enchantment";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <maven-source-dir> [output-dir]");
        }

        Path projectDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path sourceDir = Files.isDirectory(projectDir.resolve("src/main/java"))
                ? projectDir.resolve("src/main/java")
                : projectDir;
        Path outputDir = args.length >= 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceDir.resolveSibling(sourceDir.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addInputResource(sourceDir.toString());
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public boolean isToBeProcessed(CtInvocation<?> element) {
                CtExecutableReference<?> executable = element.getExecutable();
                if (executable == null || !OLD_METHOD.equals(executable.getSimpleName())) {
                    return false;
                }
                CtTypeReference<?> declaringType = executable.getDeclaringType();
                return declaringType != null && OLD_TYPE.equals(declaringType.getQualifiedName());
            }

            @Override
            public void process(CtInvocation<?> element) {
                CtTypeReference<?> returnType = element.getFactory().Type().createReference(NEW_RETURN_TYPE);
                CtExecutableReference replacement = element.getFactory().Executable()
                        .createReference(element.getFactory().Type().createReference(OLD_TYPE),
                                returnType,
                                NEW_METHOD);
                element.setExecutable(replacement);
            }
        });

        launcher.run();
    }
}
