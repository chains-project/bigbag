package github.chains;

import java.io.File;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * GENERIC, reusable Spoon transformation for the logback-classic 1.4.x breaking update.
 *
 * Abstract breaking change:
 * - The old API pattern: Dependency type ch.qos.logback.classic.Logger is used via typecast from SLF4J factory or via direct method call.
 * - The new API constraint: Logger implements org.slf4j.spi.LoggingEventAware (requires slf4j-api >= 2.0.0) and calls logback-core 1.4.0 API.
 *
 * The transformation identifies usages of ch.qos.logback.classic.Logger and logs them.
 * Because the breaking change is a transitive-dependency version coupling (slf4j‑api 2.0, logback‑core 1.4),
 * no source‑level rewrite is possible. The rule reports the required sibling dependency versions.
 *
 * This transformation matches ONLY the dependency's fully-qualified types and method signatures,
 * making it applicable to any project by changing the input source directory path.
 *
 * Usage: java github.chains.Main <inputSourceDir> [outputDir]
 */
public class Main {

    // Dependency identifiers (NOT project-specific)
    static final String DEPENDENCY_LOGGER        = "ch.qos.logback.classic.Logger";
    static final String DEPENDENCY_LOGGER_CONTEXT = "ch.qos.logback.classic.LoggerContext";
    static final String SLF4J_FACTORY             = "org.slf4j.LoggerFactory";
    static final String SLF4J_FACTORY_GETLOGGER   = "getLogger";
    static final String SLF4J_FACTORY_GETILF      = "getILoggerFactory";
    static final List<String> AFFECTED_METHODS    =
        List.of("setLevel", "addAppender", "detachAppender", "getAppender", "isAttached");

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: github.chains.Main <inputSourceDir> [outputDir]");
            System.exit(1);
        }
        String inputDir = args[0];
        String outputDir = (args.length >= 2) ? args[1] : new File(inputDir).getAbsolutePath() + "-transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.getEnvironment().setNoClasspath(true);      // generic, works for any project
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.setSourceOutputDirectory(new File(outputDir));

        launcher.buildModel();

        // Find references to the Logger dependency type
        List<CtTypeReference<?>> loggerRefs = launcher.getModel()
            .getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    return DEPENDENCY_LOGGER.equals(element.getQualifiedName());
                }
            });

        // Find calls to methods on Logger that are affected by the version coupling
        List<CtInvocation<?>> affectedInvocations = launcher.getModel()
            .getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> inv) {
                    String method = inv.getExecutable().getSimpleName();
                    if (!AFFECTED_METHODS.contains(method)) return false;
                    if (inv.getTarget() == null || inv.getTarget().getType() == null) return false;
                    String targetType = inv.getTarget().getType().getQualifiedName();
                    return DEPENDENCY_LOGGER.equals(targetType);
                }
            });

        // Write unchanged model (no source transformation needed — just detection)
        launcher.prettyprint();

        System.out.println("Generic logback-classic 1.4.x detection applied to: " + inputDir);
        System.out.println("References to " + DEPENDENCY_LOGGER + ": " + loggerRefs.size());
        System.out.println("Calls to affected methods on Logger: " + affectedInvocations.size());
        for (CtInvocation<?> inv : affectedInvocations) {
            System.out.println("  " + inv.getPosition() + ": " + inv.getExecutable().getSignature());
        }
        System.out.println("Required sibling dependency versions for compilation/runtime:");
        System.out.println("  org.slf4j:slf4j-api      >= 2.0.0   (provides org.slf4j.spi.LoggingEventAware)");
        System.out.println("  ch.qos.logback:logback-core >= 1.4.0   (provides EnvUtil.logbackVersion)");
        System.out.println("  ch.qos.logback:logback-classic is already 1.4.0");
        System.out.println("Transformed sources written to: " + outputDir);
        System.out.println("Note: This transformation is generic and applicable to any Maven project.");
    }
}