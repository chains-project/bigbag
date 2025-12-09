package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class JooqRefactoring {

    /**
     * Processor to handle the removal/relocation of methods in AbstractDatabase.
     * 
     * Analysis:
     * The diff indicates 'METHOD_REMOVED_IN_SUPERCLASS' for PostgresDatabase.
     * We target 'getProperties()' as the likely candidate requiring refactoring from 
     * direct access to a configuration-based access.
     * 
     * Transformation:
     * Original: db.getProperties()
     * Refactored: db.getConfig().getProperties()
     */
    public static class AbstractDatabaseProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"getProperties".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (getProperties takes 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner Type Check (Defensive for NoClasspath)
            // We check if the method belongs to AbstractDatabase or PostgresDatabase
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                if (qName.contains("org.jooq.meta.AbstractDatabase") || 
                    qName.contains("org.jooq.meta.postgres.PostgresDatabase")) {
                    return true;
                }
            }

            // 4. Fallback: Check target expression type if method declaration is ambiguous
            CtExpression<?> target = candidate.getTarget();
            if (target != null && target.getType() != null) {
                String targetType = target.getType().getQualifiedName();
                return targetType.contains("PostgresDatabase") || targetType.contains("AbstractDatabase");
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalTarget = invocation.getTarget();

            // Safety check: Cannot refactor static calls or null targets easily in this context
            if (originalTarget == null) {
                return;
            }

            // --- Step 1: Prepare Types ---
            // In NoClasspath, getType() might be null, so we default to Object or reconstruct
            CtTypeReference<?> configType = factory.Type().createReference("org.jooq.meta.Configuration");
            
            CtTypeReference<?> ownerType = originalTarget.getType();
            if (ownerType == null) {
                // Fallback to AbstractDatabase if type resolution fails
                ownerType = factory.Type().createReference("org.jooq.meta.AbstractDatabase");
            }
            
            CtTypeReference<?> returnType = invocation.getType();
            if (returnType == null) {
                // Fallback to java.util.Properties or Object if unknown
                returnType = factory.Type().createReference("java.util.Properties");
            }

            // --- Step 2: Create `getConfig()` invocation ---
            CtExecutableReference<Object> getConfigRef = factory.Executable().createReference(
                ownerType,
                configType,
                "getConfig"
            );

            CtInvocation<?> getConfigInvocation = factory.Code().createInvocation(
                originalTarget.clone(), // Clone to ensure strictly valid tree structure
                getConfigRef
            );

            // --- Step 3: Create `getProperties()` invocation on the Config ---
            CtExecutableReference<Object> newGetPropertiesRef = factory.Executable().createReference(
                configType,
                returnType,
                "getProperties"
            );

            CtInvocation<?> replacement = factory.Code().createInvocation(
                getConfigInvocation,
                newGetPropertiesRef
            );

            // --- Step 4: Apply Replacement ---
            invocation.replace(replacement);
            
            System.out.println("Refactored getProperties() call at " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/a1ff30e0bc6a9b48e024a8ab27cefda3ad85b530/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/a1ff30e0bc6a9b48e024a8ab27cefda3ad85b530/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/a1ff30e0bc6a9b48e024a8ab27cefda3ad85b530/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/a1ff30e0bc6a9b48e024a8ab27cefda3ad85b530/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULES ---
        // 1. Enable comments to preserve existing code documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for high-fidelity source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive Coding for NoClasspath
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new AbstractDatabaseProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}