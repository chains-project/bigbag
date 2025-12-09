package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class LiquibaseMigration {

    /**
     * Processor to migrate usages of the removed class liquibase.util.StringUtils
     * to the new replacement liquibase.util.StringUtil.
     * 
     * Specifically handles the method trimToNull(String).
     */
    public static class StringUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check method name match
            if (!"trimToNull".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check invocation target is a static type access (e.g. StringUtils.trimToNull)
            if (!(candidate.getTarget() instanceof CtTypeAccess)) {
                return false;
            }

            CtTypeAccess<?> target = (CtTypeAccess<?>) candidate.getTarget();
            CtTypeReference<?> accessedType = target.getAccessedType();

            // 3. Defensive check for NoClasspath
            if (accessedType == null) {
                return false;
            }

            String qName = accessedType.getQualifiedName();
            
            // 4. Verify owner type.
            // We want to match "liquibase.util.StringUtils" or just "StringUtils" (if unresolvable).
            // We explicitely avoid matching Apache Commons StringUtils if resolved.
            if (qName.startsWith("org.apache.commons") || qName.startsWith("java.")) {
                return false;
            }
            
            return qName.endsWith("StringUtils");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // Create reference to the new utility class: liquibase.util.StringUtil
            CtTypeReference<?> newOwnerRef = factory.Type().createReference("liquibase.util.StringUtil");

            // Create a new TypeAccess for the new owner
            CtTypeAccess<?> newOwnerAccess = factory.Code().createTypeAccess(newOwnerRef);

            // Replace the invocation target (the class access)
            // Original: [StringUtils].trimToNull(...)
            // New:      [liquibase.util.StringUtil].trimToNull(...)
            // Note: Using fully qualified name ensures compilation even if imports are not updated immediately.
            invocation.setTarget(newOwnerAccess);

            System.out.println("Refactored StringUtils.trimToNull at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/feb582661e77de66eadaa7550720a8751b266ee4/liquibase-mssql/src/java/liquibase/ext/mssql/sqlgenerator/CreateIndexGeneratorMSSQL.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/feb582661e77de66eadaa7550720a8751b266ee4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/feb582661e77de66eadaa7550720a8751b266ee4/liquibase-mssql/src/java/liquibase/ext/mssql/sqlgenerator/CreateIndexGeneratorMSSQL.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/feb582661e77de66eadaa7550720a8751b266ee4/attempt_1/transformed");

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve code structure/formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode (for running without full dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new StringUtilsProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}