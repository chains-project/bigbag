package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Collection;

public class LiquibaseStringUtilsRefactoring {

    /**
     * Processor to migrate usages of the removed class liquibase.util.StringUtils
     * to the new location liquibase.util.StringUtil.
     * <p>
     * Diff Analysis:
     * - REMOVED: liquibase.util.StringUtils.trimToNull(String)
     * - ADDED: liquibase.util.StringUtil.trimToNull(String)
     */
    public static class StringUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {

        private static final String OLD_CLASS = "liquibase.util.StringUtils";
        private static final String NEW_CLASS = "liquibase.util.StringUtil";
        private static final String METHOD_NAME = "trimToNull";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String name = candidate.getExecutable().getSimpleName();
            if (!METHOD_NAME.equals(name)) {
                return false;
            }

            // 2. Check Argument Count (trimToNull takes 1 arg)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner/Declaring Type
            // In NoClasspath, we rely on what Spoon can infer from imports or FQN usage.
            CtExecutableReference<?> execRef = candidate.getExecutable();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();

            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                // Check for exact match or simple name match if imports might map it
                return qName.equals(OLD_CLASS) || (qName.equals("StringUtils") && isLiquibaseImportPresent(candidate));
            }

            return false;
        }

        /**
         * Helper to check if the file imports the specific Liquibase StringUtils
         * to distinguish from Apache Commons StringUtils in NoClasspath mode.
         */
        private boolean isLiquibaseImportPresent(CtInvocation<?> candidate) {
            try {
                Collection<CtImport> imports = candidate.getPosition().getCompilationUnit().getImports();
                for (CtImport imp : imports) {
                    if (imp.getReference() != null && 
                        imp.getReference().getSimpleName().contains(OLD_CLASS)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Ignore if position/CU not available
            }
            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // 1. Create reference to the new class
            CtTypeReference<?> newTypeRef = factory.Type().createReference(NEW_CLASS);

            // 2. Update the Executable Reference (metadata)
            // This ensures that if Spoon regenerates the method call, it knows the new owner.
            CtExecutableReference<?> execRef = invocation.getExecutable();
            execRef.setDeclaringType(newTypeRef);

            // 3. Update the Target Expression (AST)
            // If the code is `StringUtils.trimToNull(...)`, we change the target `StringUtils` to `StringUtil`.
            if (invocation.getTarget() instanceof CtTypeAccess) {
                CtTypeAccess<?> typeAccess = (CtTypeAccess<?>) invocation.getTarget();
                typeAccess.setAccessedType(newTypeRef);
            } else if (invocation.getTarget() == null) {
                // Handle static imports if necessary, though explicit target replacement is safer
                invocation.setTarget(factory.Code().createTypeAccess(newTypeRef));
            }

            System.out.println("Refactored liquibase.util.StringUtils to StringUtil at line " 
                + (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/feb582661e77de66eadaa7550720a8751b266ee4/liquibase-mssql/src/java/liquibase/ext/mssql/sqlgenerator/AddPrimaryKeyGeneratorMSSQL.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/feb582661e77de66eadaa7550720a8751b266ee4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/feb582661e77de66eadaa7550720a8751b266ee4/liquibase-mssql/src/java/liquibase/ext/mssql/sqlgenerator/AddPrimaryKeyGeneratorMSSQL.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/feb582661e77de66eadaa7550720a8751b266ee4/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 10/11+
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Java Pretty Printer
        // This is strictly required to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath mode (robustness against missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StringUtilsProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}